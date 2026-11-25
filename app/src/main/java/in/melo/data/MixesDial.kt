/*
 * Licensed under GPL-3.0; see LICENSE. The mixes' picks (mix types, the sleep hours, the
 * community shelves and Decades) and the taste they're picked by; works on Melo's Music items,
 * with the sources read by MeloModel.
 */
package `in`.melo.data

import java.time.LocalTime
import kotlin.math.ln
import kotlin.random.Random

/**
 * The Mixes Dial: a page of mixes dealt at random, once a round (app launch or pull to refresh).
 * YouTube Music's come from Home's feed, its "Mixed for you" page and its "From the community"
 * shelf, with Melo's own stations; Spotify's are the playlists Spotify made for the listener in
 * the library. The two are dealt in turn, so neither fills the page alone. Up to
 * [DECADES_LIMIT] playlists from Explore's "Decades" category take their turn with them, and
 * what room is left goes to "Trending community playlists".
 *
 * Only one mix of a kind is dealt ([mixType]); the listener's genres are favoured ([MixTaste]);
 * sleep mixes are dealt only at night ([isSleepTime]).
 */
object MixesDial {
    /** One dial page at its largest (3 × 4). */
    const val SIZE = 12

    /** Home's shelves of other listeners' playlists: the first is dealt with the mixes, the second only fills what is left. */
    const val FROM_THE_COMMUNITY = "From the community"
    const val TRENDING_COMMUNITY = "Trending community playlists"

    /** How many more pages of Home are read looking for [FROM_THE_COMMUNITY] when the loaded ones lack it. */
    const val COMMUNITY_PAGES = 4

    /** Explore's category of playlists by decade, by its button's title. */
    const val DECADES = "Decades"

    /** How many of the [DECADES] category's playlists one deal may hold. */
    const val DECADES_LIMIT = 3

    private val SLEEP_FROM: LocalTime = LocalTime.of(21, 30)
    private val SLEEP_UNTIL: LocalTime = LocalTime.of(4, 0)

    /** Whether [time] is in the night hours sleep mixes are dealt in: 9:30pm to 4:00am. */
    fun isSleepTime(time: LocalTime = LocalTime.now()): Boolean = !time.isBefore(SLEEP_FROM) || !time.isAfter(SLEEP_UNTIL)

    fun isSleepMix(item: Music): Boolean = item.title.contains("sleep", ignoreCase = true)

    /** Spotify's own playlists made for one listener, by name, besides those called a mix. */
    private val SPOTIFY_PERSONAL = setOf(
        "discover weekly", "release radar", "on repeat", "repeat rewind", "daylist", "your top songs", "time capsule",
    )

    private fun youTubePlaylistId(item: Music): String? {
        if (item.kind != MusicKind.Playlist || !item.id.startsWith(`in`.melo.data.youtube.YTM_PLAYLIST_PREFIX)) return null
        return item.id.removePrefix(`in`.melo.data.youtube.YTM_PLAYLIST_PREFIX).removePrefix("VL")
    }

    /** A YouTube playlist of any kind, as the community shelves hold. */
    fun isYouTubePlaylist(item: Music): Boolean {
        val id = youTubePlaylistId(item) ?: return false
        return id.startsWith("PL") || id.startsWith("RD") || id.startsWith("OLAK")
    }

    /**
     * A YouTube Music mix: the account's own (`RDTMAK…`: My Mixes, Supermix, Discover Mix, New
     * Release Mix) or any other playlist that calls itself one.
     */
    fun isYouTubeMix(item: Music): Boolean {
        val id = youTubePlaylistId(item) ?: return false
        if (id.startsWith("RDTMAK")) return true
        return isYouTubePlaylist(item) && item.title.contains("mix", ignoreCase = true)
    }

    /** A playlist Spotify made for this listener, rather than an editorial one or the listener's own. */
    fun isSpotifyMix(playlist: Music): Boolean {
        if (playlist.kind != MusicKind.Playlist) return false
        if (!playlist.ownerUsername.equals("spotify", ignoreCase = true)) return false
        val name = playlist.title.trim().lowercase()
        return "mix" in name || SPOTIFY_PERSONAL.any { name.startsWith(it) }
    }

    /** The playlists of the shelf called [title] among [shelves]. */
    fun shelfPlaylists(shelves: List<YouTubeHomeShelf>, title: String): List<Music> =
        shelves.filter { it.title.trim().equals(title, ignoreCase = true) }
            .flatMap { it.items }
            .filter(::isYouTubePlaylist)

    /** The names of the mixes made for this listener: what [MixTaste] reads the listener's genres off. */
    fun madeForYou(youTube: List<Music>, spotify: List<Music>): List<String> =
        (youTube.filter { youTubePlaylistId(it)?.startsWith("RDTMAK") == true } + spotify).map { it.title }

    /**
     * A fresh deal from what was read: [home] (Home's loaded shelves, with any further pages read
     * for the community's), [mixedForYou], [stations] (Melo's own mood and station picks),
     * [decades], and [spotify] (the library's Spotify mixes). [listened] is the time listened per
     * genre, for [MixTaste].
     */
    fun deal(
        home: List<YouTubeHomeShelf>,
        mixedForYou: List<Music>,
        stations: List<Music>,
        decades: List<Music>,
        spotify: List<Music>,
        random: Random = Random(System.nanoTime()),
        sleepTime: Boolean = isSleepTime(),
        listened: Map<String, Long> = emptyMap(),
    ): List<Music> {
        val fromHome = home.flatMap { it.items }.filter(::isYouTubeMix)
        val mixed = mixedForYou.filter { it.kind == MusicKind.Playlist }
        val fromCommunity = shelfPlaylists(home, FROM_THE_COMMUNITY)
        val trending = shelfPlaylists(home, TRENDING_COMMUNITY)
        val taste = MixTaste.of(listened, madeForYou(fromHome + mixed, spotify))
        val dealt: (Music) -> Boolean = { sleepTime || !isSleepMix(it) }
        // Playlists made for everyone are only dealt in the listener's genres; their own mixes are theirs whatever they name.
        val toTaste: (Music) -> Boolean = { dealt(it) && !taste.misses(it.title) }
        return pick(
            youTube = (fromHome + mixed + stations).filter(dealt) + fromCommunity.filter(toTaste),
            spotify = spotify.filter(dealt),
            random = random,
            filler = trending.filter(toTaste),
            decades = decades.filter(toTaste),
            taste = taste,
        ).also {
            android.util.Log.d(
                "MixesDial",
                "dealt ${it.size} (home ${fromHome.size}, mixed for you ${mixed.size}, stations ${stations.size}, " +
                    "community ${fromCommunity.size}, trending ${trending.size}, decades ${decades.size}, " +
                    "spotify ${spotify.size}; taste ${if (taste.known) "known" else "unknown"})",
            )
        }
    }

    /**
     * Up to [limit] mixes: each source shuffled, then taken from in turn, so the page has both
     * while both have some. Duplicates go, and so does a second mix of a kind already dealt
     * ([mixType]). [decades] takes its turn with the two sources until [decadesLimit] of its
     * playlists are dealt. [filler] is only dealt from once both sources have run out.
     */
    fun pick(
        youTube: List<Music>,
        spotify: List<Music>,
        random: Random,
        limit: Int = SIZE,
        filler: List<Music> = emptyList(),
        decades: List<Music> = emptyList(),
        decadesLimit: Int = DECADES_LIMIT,
        taste: MixTaste = MixTaste.UNKNOWN,
    ): List<Music> {
        fun List<Music>.shuffledToTaste() = taste.shuffled(this, random, Music::title)
        val yt = youTube.distinctBy(Music::id).shuffledToTaste()
        val sp = spotify.distinctBy(Music::id).shuffledToTaste()
        val dealt = ArrayList<Music>(limit)
        val ids = HashSet<String>()
        val types = HashSet<String>()
        fun fresh(item: Music): Boolean {
            val type = mixType(item.title)
            if (item.id in ids || type in types) return false
            ids += item.id
            types += type
            return true
        }
        // Which source leads is chosen at random too.
        val sources = if (random.nextBoolean()) listOf(yt, sp) else listOf(sp, yt)
        val byDecade = if (decadesLimit > 0) decades.distinctBy(Music::id).shuffledToTaste().iterator() else emptyList<Music>().iterator()
        val decadesTurn = if (byDecade.hasNext()) random.nextInt(sources.size + 1) else -1
        var decadesDealt = 0
        fun dealDecade() {
            while (dealt.size < limit && decadesDealt < decadesLimit && byDecade.hasNext()) {
                val item = byDecade.next()
                if (fresh(item)) {
                    dealt += item
                    decadesDealt++
                    return
                }
            }
        }
        var i = 0
        while (dealt.size < limit && (i < maxOf(yt.size, sp.size) || (decadesDealt < decadesLimit && byDecade.hasNext()))) {
            for (turn in 0..sources.size) {
                if (turn == decadesTurn) dealDecade()
                val item = sources.getOrNull(turn)?.getOrNull(i) ?: continue
                if (dealt.size < limit && fresh(item)) dealt += item
            }
            i++
        }
        for (item in filler.shuffledToTaste()) {
            if (dealt.size >= limit) break
            if (fresh(item)) dealt += item
        }
        return dealt
    }

    /**
     * The kind of mix a title names: the title without case, spacing, punctuation or a number at
     * its end. "Chill Mix 01" and "Chill Mix 02" are one kind, as are "My Mix 1" and "My Mix 4".
     */
    fun mixType(title: String): String {
        val words = title.lowercase().split(NOT_A_WORD).filter { it.isNotEmpty() }
        return words.dropLastWhile { word -> word.all(Char::isDigit) }.joinToString("")
            .ifEmpty { words.joinToString("").ifEmpty { title.trim().lowercase() } }
    }

    private val NOT_A_WORD = Regex("[^\\p{L}\\p{N}]+")
}

/**
 * The genres a listener listens to, for the Mixes Dial to favour: a mix that names a genre they
 * play comes up more often, and one that names only genres they don't is passed over. A mix that
 * names no genre is neither. Genres are counted in families ([FAMILIES]). Empty [shares] means
 * the taste is not known, and then no mix is favoured or passed over.
 */
class MixTaste(private val shares: Map<String, Double>) {

    val known: Boolean get() = shares.isNotEmpty()

    fun listens(family: String): Boolean = family in shares

    /** Whether a mix called [title] names genres, none of them the listener's. */
    fun misses(title: String): Boolean {
        if (!known) return false
        val named = genresIn(title)
        return named.isNotEmpty() && named.none(::listens)
    }

    fun weight(title: String): Double {
        if (!known) return 1.0
        val named = genresIn(title)
        if (named.isEmpty()) return 1.0
        val share = named.maxOf { shares[it] ?: -1.0 }
        return if (share < 0) MISSED_WEIGHT else LISTENED_WEIGHT + LISTENED_SHARE_WEIGHT * share
    }

    /** [items] in a random order that leans by [weight]: a heavier item is likelier to be near the front. */
    fun <T> shuffled(items: List<T>, random: Random, title: (T) -> String): List<T> {
        if (!known) return items.shuffled(random)
        return items.map { it to -ln(1.0 - random.nextDouble()) / weight(title(it)) }
            .sortedBy { it.second }
            .map { it.first }
    }

    companion object {
        val UNKNOWN = MixTaste(emptyMap())

        const val LISTENED_SHARE = 0.02
        const val ENOUGH_LISTENING_MS = 60 * 60 * 1000L
        const val ENOUGH_MIX_FAMILIES = 2

        private const val MISSED_WEIGHT = 0.25
        private const val LISTENED_WEIGHT = 1.5
        private const val LISTENED_SHARE_WEIGHT = 3.0

        /**
         * The taste shown by [listened] (time listened per genre name) and by [madeForYou] (the
         * names of the mixes the services made for this listener). [UNKNOWN] when there is too
         * little of either to go on.
         */
        fun of(listened: Map<String, Long>, madeForYou: List<String> = emptyList()): MixTaste {
            val ms = HashMap<String, Long>()
            for ((genre, time) in listened) {
                if (time <= 0) continue
                for (family in genresIn(genre)) ms[family] = (ms[family] ?: 0L) + time
            }
            val total = listened.values.filter { it > 0 }.sum()
            val shares = HashMap<String, Double>()
            if (total >= ENOUGH_LISTENING_MS) {
                for ((family, time) in ms) {
                    val share = (time.toDouble() / total).coerceAtMost(1.0)
                    if (share >= LISTENED_SHARE) shares[family] = share
                }
            }
            val fromMixes = madeForYou.flatMapTo(HashSet(), ::genresIn)
            if (shares.isEmpty() && fromMixes.size < ENOUGH_MIX_FAMILIES) return UNKNOWN
            for (family in fromMixes) shares[family] = maxOf(shares[family] ?: 0.0, LISTENED_SHARE)
            return MixTaste(shares)
        }

        /** The genre families [text] names; whole words only, the longest name first. */
        fun genresIn(text: String): Set<String> {
            val words = text.lowercase().replace("&", " and ").split(NOT_A_WORD).filter { it.isNotEmpty() }
            val found = LinkedHashSet<String>()
            var i = 0
            while (i < words.size) {
                var matched = 0
                for (length in minOf(LONGEST_NAME, words.size - i) downTo 1) {
                    val families = NAMES[words.subList(i, i + length).joinToString(" ")] ?: continue
                    found += families
                    matched = length
                    break
                }
                i += maxOf(matched, 1)
            }
            return found
        }

        private val NOT_A_WORD = Regex("[^\\p{L}\\p{N}]+")

        private val FAMILIES: Map<String, List<String>> = mapOf(
            "country" to listOf("country", "americana", "bluegrass", "honky tonk", "outlaw country", "alt country"),
            "hip hop" to listOf(
                "hip hop", "hiphop", "rap", "trap", "drill", "grime", "boom bap", "phonk", "cloud rap",
                "conscious hip hop", "west coast rap", "east coast rap", "gangsta rap",
            ),
            "rock" to listOf(
                "rock", "classic rock", "hard rock", "soft rock", "pop rock", "alternative rock", "alt rock",
                "indie rock", "punk", "punk rock", "pop punk", "post punk", "grunge", "emo", "new wave", "britpop",
                "progressive rock", "prog rock", "garage rock", "psychedelic", "shoegaze", "post rock", "math rock",
                "surf rock", "rockabilly", "rock and roll", "rock n roll", "jam band", "j rock",
            ),
            "indie" to listOf(
                "indie", "indie pop", "indie rock", "indie folk", "alternative", "alt", "alternative rock",
                "alt rock", "dream pop", "art pop", "shoegaze", "bedroom pop",
            ),
            "metal" to listOf(
                "metal", "heavy metal", "nu metal", "metalcore", "hardcore", "doom metal", "black metal",
                "death metal", "thrash metal", "industrial",
            ),
            "pop" to listOf(
                "pop", "synthpop", "synth pop", "electropop", "hyperpop", "indie pop", "pop rock", "dance pop",
                "dance", "art pop", "dream pop", "bedroom pop", "teen pop", "boy bands", "boy band",
            ),
            "r&b" to listOf(
                "r and b", "rnb", "randb", "r n b", "rhythm and blues", "soul", "neo soul", "motown", "quiet storm",
                "slow jams",
            ),
            "funk" to listOf("funk", "disco", "boogie", "motown"),
            "electronic" to listOf(
                "electronic", "electronica", "electro", "edm", "house", "deep house", "tech house", "techno",
                "trance", "dubstep", "drum and bass", "drum n bass", "dnb", "jungle", "garage", "uk garage",
                "dance", "synthwave", "downtempo", "trip hop", "hardstyle", "rave",
            ),
            "jazz" to listOf("jazz", "swing", "big band", "bossa nova", "bebop", "smooth jazz"),
            "blues" to listOf("blues"),
            "classical" to listOf("classical", "opera", "orchestral", "baroque", "symphony"),
            "folk" to listOf("folk", "indie folk", "singer songwriter", "singer songwriters", "americana", "bluegrass"),
            "latin" to listOf(
                "latin", "latin pop", "latino", "latina", "reggaeton", "regueton", "salsa", "bachata", "cumbia",
                "regional mexican", "corridos", "banda", "mariachi", "urbano", "bossa nova",
            ),
            "reggae" to listOf("reggae", "dancehall", "ska", "dub"),
            "k-pop" to listOf("k pop", "kpop", "korean"),
            "j-pop" to listOf("j pop", "jpop", "j rock", "jrock", "city pop", "anime"),
            "desi" to listOf(
                "bollywood", "punjabi", "desi", "bhangra", "hindi", "sufi", "ghazal", "tamil", "telugu", "filmi",
                "indian",
            ),
            "afro" to listOf("afrobeats", "afrobeat", "afro beats", "afropop", "afro", "amapiano"),
            "christian" to listOf("christian", "gospel", "worship"),
        )

        /** Names that hold a genre's word without meaning the genre. */
        private val NOT_A_GENRE = listOf("house party", "pop culture", "country roads", "soul food")

        private val NAMES: Map<String, Set<String>> = buildMap<String, MutableSet<String>> {
            for ((family, names) in FAMILIES) for (name in names) getOrPut(name) { LinkedHashSet() } += family
            for (name in NOT_A_GENRE) put(name, LinkedHashSet())
        }

        private val LONGEST_NAME = NAMES.keys.maxOf { it.count { c -> c == ' ' } + 1 }
    }
}
