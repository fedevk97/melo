package `in`.melo.playback.auto

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import `in`.melo.R
import `in`.melo.data.ListeningHistoryStore
import `in`.melo.data.LocalLibrary
import `in`.melo.data.LocalPlaylistStore
import `in`.melo.data.Music
import `in`.melo.data.largestSpotifyImage
import `in`.melo.playback.DownloadCenter
import `in`.melo.playback.isPlayableTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * What Android Auto can browse, laid out like Spotify's: three tabs (Home, Library, Downloaded).
 * Home is a grid with a "Recently played" and a "Your playlists" section; the lists open with a
 * "Shuffle" row. Search covers the same music. Every call reads the stores again, so a change made
 * in the app shows up the next time a list is opened.
 */
@UnstableApi
internal class AutoLibrary(context: Context) {
    private val app = context.applicationContext
    private val account = AutoAccountLibrary(app)

    fun root(): MediaItem = folder(AutoIds.ROOT, app.getString(R.string.app_name))

    /** The folder or track a node id stands for, or null when it no longer exists. */
    suspend fun item(nodeId: String): MediaItem? {
        if (nodeId == AutoIds.ROOT) return root()
        folderTitle(nodeId)?.let { return folder(nodeId, it, style = styleOf(nodeId)) }
        val (parent, trackId) = AutoIds.parseTrack(nodeId) ?: return null
        if (trackId == AutoIds.SHUFFLE) return shuffleNode(parent)
        return tracks(parent).firstOrNull { it.id == trackId }?.let { trackNode(parent, it) }
    }

    suspend fun children(parentId: String, page: Int, pageSize: Int): List<MediaItem> = when (parentId) {
        AutoIds.ROOT -> AutoIds.tabs.mapNotNull { id -> folderTitle(id)?.let { folder(id, it, style = styleOf(id)) } }
            .autoPage(page, pageSize)
        AutoIds.HOME -> home().autoPage(page, pageSize)
        AutoIds.LIBRARY -> AutoIds.libraryFolders.mapNotNull { id -> folderTitle(id)?.let { folder(id, it, style = styleOf(id)) } }
            .autoPage(page, pageSize)
        AutoIds.PLAYLISTS -> playlistFolders(group = null).autoPage(page, pageSize)
        else -> listing(parentId).autoPage(page, pageSize)
    }

    /** The tracks of a folder, in the order they are listed. Empty for a folder that has none. */
    suspend fun tracks(folderId: String): List<Music> = when {
        folderId == AutoIds.RECENT -> recent()
        folderId == AutoIds.LIKED -> account.liked()
        folderId == AutoIds.LOCAL -> LocalLibrary(app).load()
        folderId == AutoIds.DOWNLOADED -> DownloadCenter.downloads.value.values.filter { it.isComplete }.map { it.track }
        AutoIds.searchQueryOf(folderId) != null -> search(AutoIds.searchQueryOf(folderId).orEmpty())
        AutoIds.savedIdOf(folderId) != null -> account.tracksOf(AutoIds.savedIdOf(folderId).orEmpty())
        else -> AutoIds.playlistIdOf(folderId)?.let { playlistStore().get(it)?.tracks }.orEmpty()
    }

    /** What the search button finds: tracks from the music Android Auto can already open. */
    suspend fun search(query: String): List<Music> {
        val store = playlistStore()
        val playlistTracks = store.playlists.value.flatMap { store.get(it.id)?.tracks.orEmpty() }
        val savedTracks = account.collections().flatMap { account.tracksOf(it.id) }
        val pool = recent() + account.liked() + tracks(AutoIds.DOWNLOADED) + playlistTracks + savedTracks + LocalLibrary(app).load()
        return pool.filter(Music::isPlayableTrack).distinctTracks().filter { matchesAutoSearch(it, query) }
    }

    /** The queue to play when [nodeId] is chosen: the folder it is in, around that track, or all of it shuffled. */
    suspend fun queueFor(nodeId: String): Pair<List<Music>, Int>? {
        val (parent, trackId) = AutoIds.parseTrack(nodeId) ?: return null
        val siblings = tracks(parent)
        if (trackId == AutoIds.SHUFFLE) return shuffledAutoQueue(siblings).takeIf { it.isNotEmpty() }?.let { it to 0 }
        return resolveAutoQueue(siblings, trackId)
    }

    /** The rows of a search, shown as the list the chosen track will be queued from. */
    suspend fun searchResults(query: String): List<MediaItem> {
        val folder = AutoIds.search(query)
        return search(query).map { trackNode(folder, it) }
    }

    private suspend fun recent(): List<Music> = withContext(Dispatchers.IO) {
        ListeningHistoryStore(app).also { it.load() }.entries.value.map { it.track }.distinctTracks()
    }

    /** Home: a grid of the latest plays, then a grid of the playlists, each under its own heading. */
    private suspend fun home(): List<MediaItem> {
        val heardTitle = app.getString(R.string.auto_recent)
        val heard = recent().filter(Music::isPlayableTrack).take(HOME_TRACKS).map { trackNode(AutoIds.RECENT, it, group = heardTitle) }
        return heard + playlistFolders(group = app.getString(R.string.auto_your_playlists))
    }

    /** The rows of a folder: a Shuffle row first when there is something to shuffle, then its tracks. */
    private suspend fun listing(folderId: String): List<MediaItem> {
        val playable = tracks(folderId).filter(Music::isPlayableTrack)
        val shuffle = if (playable.size > 1) listOf(shuffleNode(folderId)) else emptyList()
        return shuffle + playable.map { trackNode(folderId, it) }
    }

    /**
     * Liked songs first, then the playlists and albums saved in the accounts, then the playlists
     * made on this device: the same order as the app's Library.
     */
    private suspend fun playlistFolders(group: String?): List<MediaItem> {
        val liked = if (account.liked().isNotEmpty()) listOf(folder(AutoIds.LIKED, app.getString(R.string.auto_liked), group = group)) else emptyList()
        val saved = account.collections().map {
            folder(AutoIds.saved(it.id), it.title, group = group, artwork = it.imageUrl, subtitle = it.subtitle)
        }
        val device = playlistStore().playlists.value.map { folder(AutoIds.playlist(it.id), it.name, group = group) }
        return liked + saved + device
    }

    private fun playlistStore() = LocalPlaylistStore(app)

    private suspend fun folderTitle(id: String): String? = when {
        id == AutoIds.HOME -> app.getString(R.string.auto_home)
        id == AutoIds.LIBRARY -> app.getString(R.string.auto_library)
        id == AutoIds.DOWNLOADED -> app.getString(R.string.auto_downloaded)
        id == AutoIds.LIKED -> app.getString(R.string.auto_liked)
        id == AutoIds.RECENT -> app.getString(R.string.auto_recent)
        id == AutoIds.PLAYLISTS -> app.getString(R.string.auto_playlists)
        id == AutoIds.LOCAL -> app.getString(R.string.auto_local)
        AutoIds.savedIdOf(id) != null -> account.collections().firstOrNull { it.id == AutoIds.savedIdOf(id) }?.title
        else -> AutoIds.playlistIdOf(id)?.let { playlistStore().get(it)?.name }
    }

    /** Home shows its children as a grid; everything else is a list. */
    private fun styleOf(id: String) = if (id == AutoIds.HOME) GRID else LIST

    /** [style] is how this folder's own children are drawn; [group] is the heading it sits under on Home. */
    private fun folder(
        id: String, title: String, style: Int = LIST, group: String? = null, artwork: String? = null, subtitle: String? = null,
    ) = MediaItem.Builder().setMediaId(id)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setSubtitle(subtitle?.ifBlank { null })
            .setArtworkUri(largestSpotifyImage(artwork)?.let(Uri::parse)).setIsBrowsable(true).setIsPlayable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            .setExtras(Bundle().apply {
                putInt(BROWSABLE_STYLE, style)
                putInt(PLAYABLE_STYLE, style)
                if (group != null) {
                    putString(GROUP_TITLE, group)
                    putInt(SINGLE_ITEM_STYLE, GRID)
                }
            }).build()).build()

    private fun shuffleNode(folderId: String) = MediaItem.Builder().setMediaId(AutoIds.track(folderId, AutoIds.SHUFFLE))
        .setMediaMetadata(MediaMetadata.Builder().setTitle(app.getString(R.string.auto_shuffle))
            .setIsBrowsable(false).setIsPlayable(true).setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC).build()).build()

    private fun trackNode(folderId: String, track: Music, group: String? = null): MediaItem {
        val downloaded = DownloadCenter.downloads.value[track.id]?.isComplete == true
        return MediaItem.Builder().setMediaId(AutoIds.track(folderId, track.id))
            .setMediaMetadata(MediaMetadata.Builder().setTitle(track.title).setArtist(track.subtitle).setAlbumTitle(track.album.ifBlank { null })
                .setArtworkUri(largestSpotifyImage(track.imageUrl)?.let(Uri::parse))
                .setIsBrowsable(false).setIsPlayable(true).setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .setExtras(Bundle().apply {
                    putLong(DOWNLOAD_STATUS, if (downloaded) STATUS_DOWNLOADED else STATUS_NOT_DOWNLOADED)
                    if (group != null) {
                        putString(GROUP_TITLE, group)
                        putInt(SINGLE_ITEM_STYLE, GRID)
                    }
                }).build()).build()
    }

    private companion object {
        const val HOME_TRACKS = 10
        // The content style hints Android Auto reads (androidx.media.utils.MediaConstants).
        const val BROWSABLE_STYLE = "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"
        const val PLAYABLE_STYLE = "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"
        const val SINGLE_ITEM_STYLE = "android.media.browse.CONTENT_STYLE_SINGLE_ITEM_HINT"
        const val GROUP_TITLE = "android.media.browse.CONTENT_STYLE_GROUP_TITLE_HINT"
        const val DOWNLOAD_STATUS = "android.media.extra.DOWNLOAD_STATUS"
        const val STATUS_NOT_DOWNLOADED = 0L
        const val STATUS_DOWNLOADED = 2L
        const val LIST = 1
        const val GRID = 2
    }
}
