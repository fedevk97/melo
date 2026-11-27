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
    const val RECENT = "auto:recent"
    const val PLAYLISTS = "auto:playlists"
    const val LOCAL = "auto:local"
    const val HOME = "auto:home"
    const val LIBRARY = "auto:library"
    const val DOWNLOADED = "auto:downloaded"
    const val LIKED = "auto:liked"
    /** A playlist or album saved in the Spotify or YouTube Music account. */
    private const val SAVED = "auto:saved:"
    /** Stands for "play this folder in random order"; never a real track id. */
    const val SHUFFLE = "__shuffle__"
    private const val SEARCH = "auto:search:"
    private const val PLAYLIST = "auto:playlist:"
    private const val SEPARATOR = '|'

    /** The tabs Android Auto shows along the top, as in Spotify: Home, Library and Downloaded. */
    val tabs = listOf(HOME, LIBRARY, DOWNLOADED)
    val libraryFolders = listOf(LIKED, PLAYLISTS, RECENT, LOCAL)

    fun saved(id: String) = SAVED + id
    fun savedIdOf(folder: String): String? = folder.removePrefix(SAVED).takeIf { folder.startsWith(SAVED) && it.isNotEmpty() }

    fun search(query: String) = SEARCH + query
    fun searchQueryOf(folder: String): String? = folder.removePrefix(SEARCH).takeIf { folder.startsWith(SEARCH) && it.isNotBlank() }

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

/** The tracks of [siblings] in random order, for the "Shuffle" row at the top of a list. */
internal fun shuffledAutoQueue(siblings: List<Music>, random: java.util.Random = java.util.Random()): List<Music> =
    siblings.filter(Music::isPlayableTrack).shuffled(random).take(AutoLimits.QUEUE_AHEAD)

/** Whether [track] answers a search: every word typed appears in its title, artist or album. */
internal fun matchesAutoSearch(track: Music, query: String): Boolean {
    val words = query.lowercase().split(' ').filter { it.isNotBlank() }
    if (words.isEmpty()) return false
    val haystack = "${track.title} ${track.subtitle} ${track.album}".lowercase()
    return words.all { it in haystack }
}
