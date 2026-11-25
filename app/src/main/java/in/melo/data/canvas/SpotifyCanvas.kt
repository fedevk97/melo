package `in`.melo.data.canvas

import android.util.Log
import `in`.melo.data.MusicKind
import `in`.melo.data.Proto
import `in`.melo.data.SpotifyAccount
import `in`.melo.data.SpotifySearch
import `in`.melo.data.authenticatedSpotifyRequest
import `in`.melo.data.proto
import `in`.melo.data.spotifyUrl
import java.util.Locale

/**
 * Spotify's Canvas: the short looping clip some tracks run behind their cover.
 *
 * Read through the same canvas cache the Android client asks, over the signed-in session. Without a
 * session there is nothing to ask, so the lookup is skipped. A song from another source is found in
 * Spotify's catalogue first, by exact title and every credited artist.
 */
internal object SpotifyCanvas {
    private const val TAG = "SpotifyCanvas"

    /** Canvas kinds that are a video; stills and GIFs are left to the cover itself. */
    private val VIDEO_TYPES = setOf(1, 2, 3)

    suspend fun search(trackId: String?, title: String, artist: String): CanvasArtwork? {
        val session = SpotifyAccount.current() ?: return null
        val (uri, hitTitle, hitArtist) = trackId?.takeIf { it.startsWith("spotify:track:") }
            ?.let { Triple(it, title, artist) }
            ?: findTrack(title, artist)
            ?: return null
        val request = proto {
            // EntityCanvazRequest { repeated Entity entities = 1 { string entity_uri = 1 } }
            writeByteArray(1, proto { writeString(1, uri) })
        }
        val response = runCatching {
            authenticatedSpotifyRequest(
                SpotifyAccount.transport, session, spotifyUrl("canvaz-cache/v0/canvases"),
                "Spotify canvas", body = request,
            )
        }.onFailure { Log.d(TAG, "no canvas for $uri: ${it.message}") }.getOrNull() ?: return null
        val canvas = Proto(response).messages(1).firstOrNull { canvas ->
            (canvas.number(4) ?: 0) in VIDEO_TYPES && canvas.text(2).orEmpty().contains(".mp4")
        } ?: return null
        Log.d(TAG, "canvas for '$hitTitle'")
        return CanvasArtwork(
            url = canvas.text(2) ?: return null,
            title = hitTitle,
            artist = hitArtist,
            source = CanvasSource.SPOTIFY,
        )
    }

    /** The catalogue's own copy of a song, or null when no hit matches exactly. */
    private suspend fun findTrack(title: String, artist: String): Triple<String, String, String>? {
        val session = SpotifyAccount.current() ?: return null
        val page = runCatching {
            SpotifySearch(SpotifyAccount.transport, session).search("$artist $title", Locale.getDefault(), null)
        }.getOrNull() ?: return null
        val wanted = splitArtists(artist)
        if (wanted.isEmpty()) return null
        val hit = page.items.firstOrNull { item ->
            item.kind == MusicKind.Track && item.id.startsWith("spotify:track:") &&
                item.title.normalizeForMatch() == title.normalizeForMatch() &&
                splitArtists(item.subtitle).let { credited -> wanted.all { it in credited } }
        } ?: return null
        return Triple(hit.id, hit.title, hit.subtitle)
    }
}
