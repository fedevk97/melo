package `in`.melo.playback.auto

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import `in`.melo.R
import `in`.melo.data.ListeningHistoryStore
import `in`.melo.data.LocalLibrary
import `in`.melo.data.LocalPlaylistStore
import `in`.melo.data.Music
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

    /** The folder or track a node id stands for, or null when it no longer exists. */
    suspend fun item(nodeId: String): MediaItem? {
        if (nodeId == AutoIds.ROOT) return root()
        folderTitle(nodeId)?.let { return folder(nodeId, it) }
        val (parent, trackId) = AutoIds.parseTrack(nodeId) ?: return null
        return tracks(parent).firstOrNull { it.id == trackId }?.let { trackNode(parent, it) }
    }

    suspend fun children(parentId: String, page: Int, pageSize: Int): List<MediaItem> = when (parentId) {
        AutoIds.ROOT -> AutoIds.folders.mapNotNull { id -> folderTitle(id)?.let { folder(id, it) } }
            .autoPage(page, pageSize)
        AutoIds.PLAYLISTS -> playlistStore().playlists.value.map { folder(AutoIds.playlist(it.id), it.name) }
            .autoPage(page, pageSize)
        else -> tracks(parentId).filter(Music::isPlayableTrack).autoPage(page, pageSize).map { trackNode(parentId, it) }
    }

    /** The tracks of a folder, in the order they are listed. Empty for a folder that has none. */
    suspend fun tracks(folderId: String): List<Music> = when {
        folderId == AutoIds.RECENT -> withContext(Dispatchers.IO) {
            ListeningHistoryStore(app).also { it.load() }.entries.value.map { it.track }.distinctTracks()
        }
        folderId == AutoIds.LOCAL -> LocalLibrary(app).load()
        else -> AutoIds.playlistIdOf(folderId)?.let { playlistStore().get(it)?.tracks }.orEmpty()
    }

    /** The queue to play when [nodeId] is chosen: the folder it is in, around that track. */
    suspend fun queueFor(nodeId: String): Pair<List<Music>, Int>? {
        val (parent, trackId) = AutoIds.parseTrack(nodeId) ?: return null
        return resolveAutoQueue(tracks(parent), trackId)
    }

    private fun playlistStore() = LocalPlaylistStore(app)

    private fun folderTitle(id: String): String? = when {
        id == AutoIds.RECENT -> app.getString(R.string.auto_recent)
        id == AutoIds.PLAYLISTS -> app.getString(R.string.auto_playlists)
        id == AutoIds.LOCAL -> app.getString(R.string.auto_local)
        else -> AutoIds.playlistIdOf(id)?.let { playlistStore().get(it)?.name }
    }

    private fun folder(id: String, title: String) = MediaItem.Builder().setMediaId(id)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setIsBrowsable(true).setIsPlayable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED).build()).build()

    private fun trackNode(folderId: String, track: Music) = MediaItem.Builder().setMediaId(AutoIds.track(folderId, track.id))
        .setMediaMetadata(MediaMetadata.Builder().setTitle(track.title).setArtist(track.subtitle).setAlbumTitle(track.album.ifBlank { null })
            .setArtworkUri(largestSpotifyImage(track.imageUrl)?.let(Uri::parse))
            .setIsBrowsable(false).setIsPlayable(true).setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC).build()).build()
}
