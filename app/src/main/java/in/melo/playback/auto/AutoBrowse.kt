package `in`.melo.playback.auto

import `in`.melo.data.Music
import `in`.melo.playback.isPlayableTrack

/**
 * Identifiers of the nodes Android Auto browses. A folder uses its own id. A track is shown as
 * `<folder>|<trackId>`, so that choosing it also tells Melo which list it came from; the item that
 * is queued for playback keeps the plain track id the rest of the app relies on.
 */
internal object AutoIds {
    const val ROOT = "auto:root"
    const val HOME = "auto:home"
    const val RECENT = "auto:recent"
    const val LIBRARY = "auto:library"
    const val PLAYLISTS = "auto:playlists"
    const val PINNED = "auto:pinned"
    const val LOCAL = "auto:local"
    const val SEARCH = "auto:search"
    private const val PLAYLIST = "auto:playlist:"
    private const val SEPARATOR = '|'

    /** The four tabs Android Auto shows, in order. */
    val folders = listOf(HOME, RECENT, LIBRARY, LOCAL)

    fun playlist(id: String) = PLAYLIST + id
    fun playlistIdOf(folder: String): String? = folder.removePrefix(PLAYLIST).takeIf { folder.startsWith(PLAYLIST) && it.isNotEmpty() }

    fun track(folder: String, trackId: String) = "$folder$SEPARATOR$trackId"

    /** The folder and the track of a node made by [track], or null for anything else. */
    fun parseTrack(nodeId: String): Pair<String, String>? {
        val at = nodeId.indexOf(SEPARATOR)
        if (at <= 0 || at == nodeId.lastIndex) return null
        val folder = nodeId.substring(0, at)
        return if (folder.startsWith("auto:")) folder to nodeId.substring(at + 1) else null
    }
}

/** What Android Auto is given, and what Melo queues, are bounded: a long library never reaches the player whole. */
internal object AutoLimits {
    /** Android Auto shows a limited number of rows per list anyway. */
    const val PAGE_SIZE = 100
    const val QUEUE_BEHIND = 50
    const val QUEUE_AHEAD = 450
}

internal fun <T> List<T>.autoPage(page: Int, pageSize: Int): List<T> {
    val size = pageSize.coerceIn(1, AutoLimits.PAGE_SIZE)
    val from = page.coerceAtLeast(0).toLong() * size
    if (from >= this.size) return emptyList()
    return subList(from.toInt(), minOf(this.size.toLong(), from + size).toInt())
}

/** The tracks to queue when [trackId] is chosen in [siblings], and the position of that track among them. */
internal fun resolveAutoQueue(siblings: List<Music>, trackId: String): Pair<List<Music>, Int>? {
    val playable = siblings.filter(Music::isPlayableTrack)
    val chosen = playable.indexOfFirst { it.id == trackId }
    if (chosen < 0) return null
    val from = (chosen - AutoLimits.QUEUE_BEHIND).coerceAtLeast(0)
    val to = (chosen + AutoLimits.QUEUE_AHEAD + 1).coerceAtMost(playable.size)
    return playable.subList(from, to) to (chosen - from)
}

/** Several plays of the same track in a history list are shown once, at the newest play. */
internal fun List<Music>.distinctTracks(): List<Music> = distinctBy(Music::id)

/** The content-style hints Android Auto reads from a browsable item's extras (values from androidx.media.utils.MediaConstants). */
internal object AutoStyle {
    const val SUPPORTED = "android.media.browse.CONTENT_STYLE_SUPPORTED"
    const val BROWSABLE = "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"
    const val PLAYABLE = "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"
    const val GROUP_TITLE = "android.media.browse.CONTENT_STYLE_GROUP_TITLE_HINT"
    const val SEARCH_SUPPORTED = "android.media.browse.SEARCH_SUPPORTED"
    const val LIST = 1
    const val GRID = 2
}

/** Tracks whose title, artist or album contain [query], ignoring case; blank queries match nothing. */
internal fun List<Music>.matching(query: String): List<Music> {
    val needle = query.trim()
    if (needle.isEmpty()) return emptyList()
    return filter { it.title.contains(needle, ignoreCase = true) || it.subtitle.contains(needle, ignoreCase = true) ||
        it.album.contains(needle, ignoreCase = true) }
}
