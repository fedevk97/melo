package `in`.melo.playback.auto

import android.content.ContentResolver
import android.content.Context
import android.os.Bundle
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaLibraryService.LibraryParams
import `in`.melo.R
import `in`.melo.data.ListeningHistoryStore
import `in`.melo.data.LocalLibrary
import `in`.melo.data.LocalPlaylistStore
import `in`.melo.data.Music
import `in`.melo.data.MusicKind
import `in`.melo.data.PinnedStore
import `in`.melo.data.largestSpotifyImage
import `in`.melo.playback.isPlayableTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * What Android Auto can browse: recently played tracks, the playlists kept on this device and the
 * music stored on it. Every call reads the stores again, so a change made in the app shows up the
 * next time the list is opened.
 */
internal class AutoLibrary(context: Context) {
    private val app = context.applicationContext

    fun root(): MediaItem = folder(AutoIds.ROOT, app.getString(R.string.app_name))

    /** Tells Android Auto that Melo has a search button, and how to draw lists by default. */
    fun rootParams(): LibraryParams = LibraryParams.Builder().setExtras(Bundle().apply {
        putBoolean(AutoStyle.SUPPORTED, true)
        putBoolean(AutoStyle.SEARCH_SUPPORTED, true)
        putInt(AutoStyle.BROWSABLE, AutoStyle.LIST)
        putInt(AutoStyle.PLAYABLE, AutoStyle.LIST)
    }).build()

    /** The folder or track a node id stands for, or null when it no longer exists. */
    suspend fun item(nodeId: String): MediaItem? {
        if (nodeId == AutoIds.ROOT) return root()
        folderTitle(nodeId)?.let { return folder(nodeId, it) }
        val (parent, trackId) = AutoIds.parseTrack(nodeId) ?: return null
        val pool = if (parent == AutoIds.SEARCH) searchPool() else tracks(parent)
        return pool.firstOrNull { it.id == trackId }?.let { trackNode(parent, it) }
    }

    suspend fun children(parentId: String, page: Int, pageSize: Int): List<MediaItem> = when (parentId) {
        AutoIds.ROOT -> AutoIds.folders.mapNotNull { id -> folderTitle(id)?.let { folder(id, it) } }
            .autoPage(page, pageSize)
        AutoIds.LIBRARY -> listOf(AutoIds.PLAYLISTS, AutoIds.PINNED).mapNotNull { id -> folderTitle(id)?.let { folder(id, it) } }
            .autoPage(page, pageSize)
        AutoIds.PLAYLISTS -> playlistStore().playlists.value.map { folder(AutoIds.playlist(it.id), it.name) }
            .autoPage(page, pageSize)
        AutoIds.HOME -> {
            val pinned = pinnedTracks().map { it.id }.toSet()
            tracks(parentId).filter(Music::isPlayableTrack).autoPage(page, pageSize).map {
                trackNode(parentId, it, group = app.getString(if (it.id in pinned) R.string.auto_pinned else R.string.auto_recent))
            }
        }
        else -> tracks(parentId).filter(Music::isPlayableTrack).autoPage(page, pageSize).map { trackNode(parentId, it) }
    }

    /** The tracks of a folder, in the order they are listed. Empty for a folder that has none. */
    suspend fun tracks(folderId: String): List<Music> = when {
        folderId == AutoIds.RECENT -> recent()
        folderId == AutoIds.HOME -> {
            val pinned = pinnedTracks()
            val seen = pinned.map { it.id }.toSet()
            pinned + recent().filter { it.id !in seen }.take(HOME_RECENT)
        }
        folderId == AutoIds.PINNED -> pinnedTracks()
        folderId == AutoIds.LOCAL -> LocalLibrary(app).load()
        else -> AutoIds.playlistIdOf(folderId)?.let { playlistStore().get(it)?.tracks }.orEmpty()
    }

    /** Everything the search button looks through: history, pins, the device and the saved playlists. */
    suspend fun searchPool(): List<Music> =
        (recent() + pinnedTracks() + LocalLibrary(app).load() + playlistStore().playlists.value.flatMap { it.tracks })
            .filter(Music::isPlayableTrack).distinctTracks()

    suspend fun search(query: String): List<Music> = searchPool().matching(query)

    suspend fun searchResults(query: String, page: Int, pageSize: Int): List<MediaItem> =
        search(query).autoPage(page, pageSize).map { trackNode(AutoIds.SEARCH, it) }

    /** The queue to play when [nodeId] is chosen: the folder it is in, around that track. A search result plays alone. */
    suspend fun queueFor(nodeId: String): Pair<List<Music>, Int>? {
        val (parent, trackId) = AutoIds.parseTrack(nodeId) ?: return null
        if (parent == AutoIds.SEARCH) return searchPool().firstOrNull { it.id == trackId }?.let { listOf(it) to 0 }
        return resolveAutoQueue(tracks(parent), trackId)
    }

    private suspend fun recent(): List<Music> = withContext(Dispatchers.IO) {
        ListeningHistoryStore(app).also { it.load() }.entries.value.map { it.track }.distinctTracks()
    }

    private fun pinnedTracks(): List<Music> = PinnedStore(app).pins.value.filter { it.kind == MusicKind.Track }

    private fun playlistStore() = LocalPlaylistStore(app)

    private fun folderTitle(id: String): String? = when {
        id == AutoIds.HOME -> app.getString(R.string.auto_home)
        id == AutoIds.RECENT -> app.getString(R.string.auto_recent)
        id == AutoIds.LIBRARY -> app.getString(R.string.auto_library)
        id == AutoIds.PLAYLISTS -> app.getString(R.string.auto_playlists)
        id == AutoIds.PINNED -> app.getString(R.string.auto_pinned)
        id == AutoIds.LOCAL -> app.getString(R.string.auto_local)
        else -> AutoIds.playlistIdOf(id)?.let { playlistStore().get(it)?.name }
    }

    /** The icon a tab is drawn with: a vector from the app's resources, which Android Auto tints. */
    private fun iconOf(id: String): Uri? = when (id) {
        AutoIds.HOME -> R.drawable.rounded_home_24
        AutoIds.RECENT -> R.drawable.rounded_history_24
        AutoIds.LIBRARY -> R.drawable.rounded_library_music_24
        AutoIds.LOCAL -> R.drawable.rounded_smartphone_24
        else -> null
    }?.let { Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://${app.packageName}/$it") }

    /** Home shows covers in a grid, as Spotify's does; every other list is rows. */
    private fun styleOf(id: String) = Bundle().apply {
        putInt(AutoStyle.PLAYABLE, if (id == AutoIds.HOME) AutoStyle.GRID else AutoStyle.LIST)
        putInt(AutoStyle.BROWSABLE, AutoStyle.LIST)
    }

    private fun folder(id: String, title: String) = MediaItem.Builder().setMediaId(id)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setIsBrowsable(true).setIsPlayable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED).setArtworkUri(iconOf(id)).setExtras(styleOf(id)).build()).build()

    private fun trackNode(folderId: String, track: Music, group: String? = null) = MediaItem.Builder().setMediaId(AutoIds.track(folderId, track.id))
        .setMediaMetadata(MediaMetadata.Builder().setTitle(track.title).setArtist(track.subtitle).setAlbumTitle(track.album.ifBlank { null })
            .setArtworkUri(largestSpotifyImage(track.imageUrl)?.let(Uri::parse))
            .setIsBrowsable(false).setIsPlayable(true).setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .apply { if (group != null) setExtras(Bundle().apply { putString(AutoStyle.GROUP_TITLE, group) }) }.build()).build()

    private companion object { const val HOME_RECENT = 40 }
}
