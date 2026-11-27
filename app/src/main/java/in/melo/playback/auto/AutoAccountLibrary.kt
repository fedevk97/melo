package `in`.melo.playback.auto

import android.content.Context
import `in`.melo.MeloModel
import `in`.melo.data.CatalogStore
import `in`.melo.data.Music
import `in`.melo.data.MusicKind
import `in`.melo.data.decodePins
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The music saved in the Spotify and YouTube Music accounts, read from what the app last stored on
 * the phone: liked songs, saved playlists and albums, and the tracks of each one. Android Auto
 * cannot sign in or wait for the network, so a list the app has never opened or stored shows empty.
 */
internal class AutoAccountLibrary(context: Context) {
    private val app = context.applicationContext
    private val store = CatalogStore(app)

    suspend fun liked(): List<Music> = withContext(Dispatchers.IO) { store.loadLibrary()?.savedTracks.orEmpty() }

    /** Saved playlists (Spotify, then YouTube Music), then saved albums, each once. */
    suspend fun collections(): List<Music> = withContext(Dispatchers.IO) {
        val library = store.loadLibrary()
        val youTube = decodePins(app.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(KEY_YOUTUBE_PLAYLISTS, null))
        accountCollections(library?.playlists.orEmpty(), youTube, library?.albums.orEmpty())
    }

    suspend fun tracksOf(collectionId: String): List<Music> = withContext(Dispatchers.IO) {
        store.loadDetail(collectionId)?.tracks.orEmpty()
    }

    private companion object {
        // Where MeloModel keeps the YouTube Music playlists it last read (its "music_library" preferences).
        const val PREFERENCES = "music_library"
        const val KEY_YOUTUBE_PLAYLISTS = "youtube_playlists"
    }
}

/** The playlists and albums to list, without the Liked songs entry (it has its own row) or repeats. */
internal fun accountCollections(spotify: List<Music>, youTube: List<Music>, albums: List<Music>): List<Music> =
    (spotify + youTube + albums)
        .filter { it.id != MeloModel.LIKED_SONGS && (it.kind == MusicKind.Playlist || it.kind == MusicKind.Album) }
        .distinctBy(Music::id)
