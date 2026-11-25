package `in`.melo.data.canvas

import java.text.Normalizer
import java.util.Locale

/** Where a looping cover came from; the share sheet offers one choice per source. */
enum class CanvasSource { SPOTIFY, APPLE, OTHER }

/**
 * A looping video that stands in for a track's cover art: a Canvas, a motion sleeve or a video cover.
 *
 * [fallbackUrl] is a second rendition of the same clip, tried when [url] won't play. Providers search
 * by free text and return near misses freely, so [matches] re-checks the answer against the song.
 */
data class CanvasArtwork(
    val url: String,
    val fallbackUrl: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val source: CanvasSource = CanvasSource.OTHER,
    /** The same clip framed 3:4, when the provider has one; a story-shaped video takes it first. */
    val portraitUrl: String? = null,
) {
    /**
     * Title and every wanted artist must match once case, accents and punctuation are folded away.
     * The album only counts when both sides know it.
     */
    fun matches(wantTitle: String, wantArtist: String, wantAlbum: String?): Boolean {
        val titleOk = title == null || wantTitle.isBlank() ||
            title.normalizeForMatch() == wantTitle.normalizeForMatch()
        val wanted = splitArtists(wantArtist)
        val ours = splitArtists(artist.orEmpty())
        val artistOk = artist == null || wantArtist.isBlank() ||
            (wanted.isNotEmpty() && ours.isNotEmpty() && wanted.all { want -> ours.any { it == want } })
        val albumOk = album.isNullOrBlank() || wantAlbum.isNullOrBlank() ||
            album.normalizeForMatch() == wantAlbum.normalizeForMatch()
        return titleOk && artistOk && albumOk
    }
}

/** Case, accents and punctuation folded away, so services' spellings of one release compare equal. */
fun String.normalizeForMatch(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        .lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9\\s]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

/** One credit line into its artists, whatever separator the service uses. */
fun splitArtists(raw: String): List<String> =
    raw.split(ARTIST_SEPARATORS).map { it.normalizeForMatch() }.filter { it.isNotBlank() }

private val ARTIST_SEPARATORS = Regex(
    "(?:\\s*,\\s*|\\s*&\\s*|\\s+×\\s+|\\s+x\\s+|\\bfeat\\.?\\b|\\bft\\.?\\b|\\bfeaturing\\b|\\bwith\\b)",
    RegexOption.IGNORE_CASE,
)
