package `in`.melo.data.canvas

import `in`.melo.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * The looping covers a song can be shared over, one per source.
 *
 * Apple's motion sleeve and Spotify's Canvas are looked up side by side; the third choice is Tidal's
 * video cover, or the community index when Tidal has none. Every answer is re-checked against the
 * song, and answers, misses included, are remembered for the session.
 */
object CanvasRepository {
    private val cache = object : LinkedHashMap<String, Map<CanvasSource, CanvasArtwork>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Map<CanvasSource, CanvasArtwork>>) =
            size > CACHE_SIZE
    }
    private const val CACHE_SIZE = 64

    /** The clips already found for [song], if it was looked up this session. */
    fun cached(song: Song): Map<CanvasSource, CanvasArtwork>? =
        synchronized(cache) { cache["${song.id}|${song.title}|${song.artist}|${song.album}"] }

    suspend fun shareClipsFor(song: Song): Map<CanvasSource, CanvasArtwork> {
        if (song.isLocal && song.title.isBlank()) return emptyMap()
        val key = "${song.id}|${song.title}|${song.artist}|${song.album}"
        synchronized(cache) { cache[key] }?.let { return it }
        val found = lookUp(song)
        synchronized(cache) { cache[key] = found }
        return found
    }

    private suspend fun lookUp(song: Song): Map<CanvasSource, CanvasArtwork> = coroutineScope {
        val title = song.title.cleaned()
        val artist = song.artist.cleaned()
        if (title.isBlank() || artist.isBlank()) return@coroutineScope emptyMap()
        val album = song.album.takeIf { it.isNotBlank() }
        fun CanvasArtwork?.ok() = this?.takeIf { it.matches(title, artist, album) }
        val apple = async(Dispatchers.IO) {
            runCatching { AppleMusicCanvas.search(title, artist, album) }.getOrNull().ok()
        }
        val spotify = async(Dispatchers.IO) {
            runCatching { SpotifyCanvas.search(song.id, title, artist) }.getOrNull().ok()
        }
        val other = async(Dispatchers.IO) {
            runCatching { TidalCanvas.search(title, artist, album) }.getOrNull().ok()
                ?: runCatching { CommunityCanvas.search(title, artist, album) }.getOrNull().ok()
        }
        buildMap {
            other.await()?.let { put(CanvasSource.OTHER, it) }
            apple.await()?.let { put(CanvasSource.APPLE, it) }
            spotify.await()?.let { put(CanvasSource.SPOTIFY, it) }
        }
    }

    /** Upload noise such as "(Official Video)" or "| Movie Name" stripped from a title or credit. */
    internal fun String.cleaned(): String =
        replace(NOISE, "").substringBefore(" | ").replace(Regex("\\s+"), " ").trim()

    private val NOISE = Regex(
        "\\((?:from|official|lyrical|video|audio)[^)]*\\)|\\[[^]]*]|" +
            "\\b(?:official (?:video|audio|music video)|lyrical|full song|4k video)\\b",
        RegexOption.IGNORE_CASE,
    )
}
