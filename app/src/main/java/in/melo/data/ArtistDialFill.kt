/*
 * Licensed under GPL-3.0; see LICENSE. Works on Melo's Music items, Spotify and local artists
 * kept, and the played-most artists read from Melo's own listening history.
 */
package `in`.melo.data

import `in`.melo.data.youtube.YTM_ARTIST_PREFIX
import `in`.melo.data.youtube.YouTubePages
import `in`.melo.data.youtube.youTubeVideoId
import java.util.Locale

/** The artists' dial's tiles, filled from history, pins and the feed. */
object ArtistDialFill {
    /** As many tiles as the dial holds. */
    const val SIZE = 36

    /** A play counts for at least this long, so short songs aren't ranked below a few long ones. */
    private const val MIN_PLAY_MS = 30_000L

    /**
     * The pins, then the artists played most in the app, then the account's artists (its
     * history's, then its library's), then the artists in Home's feed. Without the fill a new
     * install (no pins, nothing played yet) would have no tiles, and an empty dial isn't shown.
     */
    fun tiles(
        pins: List<Music>,
        mostPlayed: List<Music>,
        account: List<Music>,
        library: List<Music>,
        feed: List<Music>,
        hidden: Set<String> = emptySet(),
        size: Int = SIZE,
    ): List<Music> {
        val fill = (account + library + feed).mapNotNull(::asArtist)
        return (pins + mostPlayed + fill)
            .distinctBy(Music::id)
            .filterNot { it.id in hidden }
            .take(maxOf(size, pins.size))
    }

    /**
     * The artists played most in the app, all time, by listening time. A song's artists are the
     * ones YouTube gave with it when known, else its subtitle's first name matched to an artist
     * Melo already knows (the account's, the library's or the feed's), since a tile needs a page
     * to open.
     */
    fun mostPlayed(history: List<HistoryEntry>, known: List<Music>): List<Music> {
        val byName = HashMap<String, Music>()
        known.mapNotNull(::asArtist).forEach { byName.putIfAbsent(key(it.title), it) }
        val time = LinkedHashMap<String, Long>()
        val artists = HashMap<String, Music>()
        history.asSequence().filter { it.track.kind == MusicKind.Track }.forEach { entry ->
            val played = entry.track.durationMs.toLong().coerceAtLeast(MIN_PLAY_MS)
            val related = entry.track.youTubeVideoId
                ?.let { YouTubePages.rememberedRelations(it)?.artists }
                .orEmpty().mapNotNull(::asArtist)
            val credited = related.ifEmpty {
                listOfNotNull(entry.track.subtitle.split(" · ", ",", ";", " & ").firstOrNull()
                    ?.trim()?.takeIf(String::isNotBlank)?.let { byName[key(it)] })
            }
            credited.forEach { artist ->
                // Known artists keep the photo Melo already has for them.
                artists.putIfAbsent(artist.id, byName[key(artist.title)]?.takeIf { it.id == artist.id } ?: artist)
                time[artist.id] = (time[artist.id] ?: 0L) + played
            }
        }
        return time.entries.sortedByDescending { it.value }.mapNotNull { artists[it.key] }
    }

    /**
     * [item] when it is an artist that has a page, else null. YouTube artists must be channels:
     * the library gives some as their library page ("MPLA" + the channel id), which the dial
     * opens as the artist's own page; other YouTube browse ids aren't artists.
     */
    fun asArtist(item: Music): Music? {
        if (item.kind != MusicKind.Artist) return null
        if (!item.id.startsWith(YTM_ARTIST_PREFIX)) return item
        val browseId = item.id.removePrefix(YTM_ARTIST_PREFIX)
        val channel = browseId.removePrefix("MPLA")
        if (!channel.startsWith("UC")) return null
        return if (channel == browseId) item else item.copy(id = YTM_ARTIST_PREFIX + channel)
    }

    private fun key(name: String) = name.trim().lowercase(Locale.ROOT)
}
