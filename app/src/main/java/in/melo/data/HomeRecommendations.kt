package `in`.melo.data

import org.json.JSONArray
import org.json.JSONObject

/** The card types of the Top Picks row, in Melo's catalogue model. */
enum class HomeRecommendationType {
    LATEST_RELEASE,
    PERSONAL_STATION,
    MOOD,
    ARTIST_AND_SIMILAR,
    FEATURED_ARTIST,
    NEW_RELEASE_MIX,
    DISCOVERY_STATION,
}

/** The queue endpoint YouTube Music returned for a mix, radio, or artist header button. */
data class YouTubeWatchTarget(
    val playlistId: String,
    val videoId: String? = null,
    val params: String? = null,
)

/** A recommendation-history row keeps the channel ID the account linked from the artist credit. */
data class HomeHistoryTrack(
    val track: Music,
    val artists: List<Music> = emptyList(),
    val isEpisode: Boolean = false,
) {
    val primaryArtist: Music? get() = artists.firstOrNull { it.id.startsWith("ytm:artist:UC") }
}

data class HomeArtistRelease(val item: Music, val singleOrEp: Boolean)

data class HomeArtistRecommendations(
    val artist: Music,
    val releases: List<HomeArtistRelease>,
    val relatedArtists: List<Music>,
    val monthlyListeners: String? = null,
    val mixTarget: YouTubeWatchTarget? = null,
    val shuffleTarget: YouTubeWatchTarget? = null,
)

data class HomeMoodPlaylistShelf(val title: String, val playlists: List<Music>)

/** A category chip from YouTube Music's Home header (for example, Feel Good or Relax). */
data class YouTubeHomeChip(val title: String, val params: String)

/** One shelf in the personalized YouTube Music Home feed. */
data class YouTubeHomeShelf(val title: String, val items: List<Music>)

/** A Home page and the continuation YouTube Music returned for it. */
data class YouTubeHomePage(val shelves: List<YouTubeHomeShelf>, val continuation: String? = null)

/** The active Home category and its personalized feed, kept out of the library shelves. */
data class YouTubeHomeState(
    val chips: List<YouTubeHomeChip> = emptyList(),
    val selected: YouTubeHomeChip? = null,
    val shelves: List<YouTubeHomeShelf> = emptyList(),
    val continuation: String? = null,
    /** The unfiltered Home page plus new-release and Explore supplements. */
    val personalizedShelves: List<YouTubeHomeShelf> = emptyList(),
    val personalizedContinuation: String? = null,
    val personalizedLoading: Boolean = false,
    val personalizedLoadingMore: Boolean = false,
    val personalizedError: String? = null,
    val loadingCategories: Boolean = false,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    /**
     * When the personalized feed last arrived from YouTube Music (0 while only the saved one is
     * shown): the dials deal their round's tiles once it has.
     */
    val personalizedLoadedAt: Long = 0L,
)

/** A server-defined mood or genre card from YouTube Music Explore. */
data class YouTubeExploreCategory(
    val title: String,
    val browseId: String,
    val params: String? = null,
    val stripeColor: Long? = null,
    val imageUrl: String? = null,
) {
    val key: String get() = "$browseId|${params.orEmpty()}"
}

data class YouTubeExploreSection(val title: String, val categories: List<YouTubeExploreCategory>)

data class YouTubeExploreState(
    val sections: List<YouTubeExploreSection> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val selected: YouTubeExploreCategory? = null,
    val shelves: List<YouTubeHomeShelf> = emptyList(),
    val loadingCategory: Boolean = false,
    val categoryError: String? = null,
)

/** Melo's recent local chart, ordered by artist, song, and genre. */
data class HomeLocalTaste(
    val artists: List<String> = emptyList(),
    val songs: List<Music> = emptyList(),
    val genres: List<String> = emptyList(),
)

data class HomeRecommendationPick(
    val item: Music,
    val type: HomeRecommendationType,
    val title: String = item.title,
    val subtitle: String = item.subtitle,
    val artworkUrls: List<String> = listOfNotNull(item.imageUrl),
    val artistName: String? = null,
    val artistBrowseId: String? = null,
    val watchTarget: YouTubeWatchTarget? = null,
    val mood: HomeMood? = null,
    /** The history video IDs a fallback Discovery Station must leave out. */
    val excludeVideoIds: List<String> = emptyList(),
)

/** YouTube Music's eleven fixed categories, with mood and activity shelves. */
enum class HomeMood(val categoryParams: String, val shelfWords: List<String> = emptyList()) {
    ENERGY(ENERGIZE),
    RELAX(CHILL_CATEGORY, listOf("unwind", "peaceful", "relaxing", "laidback")),
    FEELING_BLUE(SAD, listOf("sad", "melanchol", "lonely")),
    FEEL_GOOD(FEEL_GOOD_CATEGORY),
    FOCUS(FOCUS_CATEGORY),
    HEARTBREAK(SAD, listOf("heartbreak", "breakup", "break-up", "broken", "heartache")),
    LOVE(ROMANCE),
    AFTER_HOURS(CHILL_CATEGORY, listOf("deep grooves", "electronic", "jazz", "r&b")),
    ALONE_TIME(CHILL_CATEGORY, listOf("mellow", "acoustic", "laidback")),
    CHILL(CHILL_CATEGORY, listOf("chill", "chilled")),
    COMMUTING(COMMUTE),
    EATING_COOKING(CHILL_CATEGORY, listOf("coffee", "soul", "jazz", "r&b")),
    EVENING(CHILL_CATEGORY, listOf("unwind", "acoustic", "laidback", "jazz")),
    FITNESS(WORKOUT, listOf("gym", "cardio", "essentials")),
    GAMING(GAMING_CATEGORY),
    HOME(CHILL_CATEGORY, listOf("coffee shop", "chill pop", "laidback")),
    MORNING(FEEL_GOOD_CATEGORY, listOf("happy", "confident", "pop")),
    MOTIVATION(ENERGIZE, listOf("motivation", "power", "beast")),
    OUTDOORS(WORKOUT, listOf("outdoor", "running")),
    PARTY(PARTY_CATEGORY),
    SLEEP(SLEEP_CATEGORY),
    SOCIAL(PARTY_CATEGORY, listOf("party today", "chill party", "throwback")),
    SPORTS(ENERGIZE, listOf("beast", "power", "hip-hop")),
    SUMMERTIME(FEEL_GOOD_CATEGORY, listOf("dance", "kicking back", "feel-good pop")),
    HOLIDAY(FEEL_GOOD_CATEGORY, listOf("throwback", "happy")),
    WEEKEND(PARTY_CATEGORY, listOf("party today", "dance")),
    WELLBEING(CHILL_CATEGORY, listOf("mindfulness", "meditation", "yoga")),
    WORK(FOCUS_CATEGORY, listOf("flow", "instrumental", "reading"));

    val isCoreMood: Boolean get() = ordinal <= LOVE.ordinal

    companion object {
        const val CATEGORY_BROWSE_ID = "FEmusic_moods_and_genres_category"
        val coreMoods: List<HomeMood> get() = entries.filter(HomeMood::isCoreMood)
    }
}

data class HomeRecommendationState(
    val picks: List<HomeRecommendationPick> = emptyList(),
    val youtubeRecent: List<Music> = emptyList(),
    val youtubeArtists: List<Music> = emptyList(),
    val youtubeMixes: List<Music> = emptyList(),
    val loading: Boolean = false,
)

/** Hour cache. Recent tracks are kept alongside picks for Melo's Recents feed. */
data class HomeRecommendationCache(
    val listener: String,
    val hour: String,
    val state: HomeRecommendationState,
)

/** Small device-local codec; recommendation picks have no account credentials or playback data. */
object HomeRecommendationCacheCodec {
    fun encode(cache: HomeRecommendationCache): String = JSONObject()
        .put("version", 3)
        .put("listener", cache.listener)
        .put("hour", cache.hour)
        .put("picks", JSONArray().apply { cache.state.picks.forEach { put(encodePick(it)) } })
        .put("recent", JSONArray().apply { cache.state.youtubeRecent.forEach { put(encodeMusic(it)) } })
        .put("artists", JSONArray().apply { cache.state.youtubeArtists.forEach { put(encodeMusic(it)) } })
        .put("mixes", JSONArray().apply { cache.state.youtubeMixes.forEach { put(encodeMusic(it)) } })
        .toString()

    fun decode(raw: String?): HomeRecommendationCache? = runCatching {
        val json = JSONObject(raw ?: return null)
        val version = json.optInt("version")
        if (version !in 2..3) return null
        val picks = json.optJSONArray("picks").objects().mapNotNull(::decodePick)
        val recent = json.optJSONArray("recent").objects().mapNotNull(::decodeMusic)
        val artists = json.optJSONArray("artists").objects().mapNotNull(::decodeMusic)
        val mixes = json.optJSONArray("mixes").objects().mapNotNull(::decodeMusic)
        HomeRecommendationCache(
            listener = json.optString("listener"),
            hour = json.optString("hour"),
            state = HomeRecommendationState(picks, recent, artists, mixes),
        )
    }.getOrNull()

    private fun encodePick(pick: HomeRecommendationPick) = JSONObject()
        .put("type", pick.type.name)
        .put("item", encodeMusic(pick.item))
        .put("title", pick.title)
        .put("subtitle", pick.subtitle)
        .put("artwork", JSONArray(pick.artworkUrls))
        .put("artistName", pick.artistName)
        .put("artistBrowseId", pick.artistBrowseId)
        .put("mood", pick.mood?.name)
        .put("exclude", JSONArray(pick.excludeVideoIds))
        .put("watch", pick.watchTarget?.let { target ->
            JSONObject().put("playlistId", target.playlistId).put("videoId", target.videoId).put("params", target.params)
        })

    private fun decodePick(json: JSONObject): HomeRecommendationPick? = runCatching {
        val item = decodeMusic(json.optJSONObject("item")) ?: return null
        val type = HomeRecommendationType.valueOf(json.getString("type"))
        val targetJson = json.optJSONObject("watch")
        HomeRecommendationPick(
            item = item,
            type = type,
            title = json.optString("title", item.title),
            subtitle = json.optString("subtitle", item.subtitle),
            artworkUrls = json.optJSONArray("artwork").strings(),
            artistName = json.optNullableString("artistName"),
            artistBrowseId = json.optNullableString("artistBrowseId"),
            watchTarget = targetJson?.let {
                YouTubeWatchTarget(it.optString("playlistId"), it.optNullableString("videoId"), it.optNullableString("params"))
            },
            mood = json.optNullableString("mood")?.let(HomeMood::valueOf),
            excludeVideoIds = json.optJSONArray("exclude").strings(),
        )
    }.getOrNull()

    private fun encodeMusic(item: Music) = JSONObject()
        .put("id", item.id).put("title", item.title).put("subtitle", item.subtitle)
        .put("kind", item.kind.name).put("image", item.imageUrl).put("note", item.note)
        .put("album", item.album).put("duration", item.durationMs).put("genre", item.genre)

    private fun decodeMusic(json: JSONObject?): Music? = runCatching {
        if (json == null) return null
        Music(
            id = json.getString("id"), title = json.getString("title"), subtitle = json.optString("subtitle"),
            kind = MusicKind.valueOf(json.getString("kind")), imageUrl = json.optNullableString("image"),
            note = json.optString("note"), album = json.optString("album"), durationMs = json.optInt("duration"),
            genre = json.optString("genre"),
        )
    }.getOrNull()

    private fun JSONObject.optNullableString(key: String): String? = optString(key).takeIf { it.isNotBlank() && it != "null" }
    private fun JSONArray?.objects(): List<JSONObject> = this?.let { array ->
        (0 until array.length()).mapNotNull(array::optJSONObject)
    }.orEmpty()
    private fun JSONArray?.strings(): List<String> = this?.let { array ->
        (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
    }.orEmpty()

}

private const val CHILL_CATEGORY = "ggMPOg1uX1JOQWZFeDByc2Jm"
private const val COMMUTE = "ggMPOg1uX044Z2o5WERLckpU"
private const val ENERGIZE = "ggMPOg1uX2lRZUZiMnNrQnJW"
private const val FEEL_GOOD_CATEGORY = "ggMPOg1uXzZQbDB5eThLRTQ3"
private const val FOCUS_CATEGORY = "ggMPOg1uX0NvNGNhWThMYWRh"
private const val GAMING_CATEGORY = "ggMPOg1uX3NmUVV4Vzl3WGQ0"
private const val PARTY_CATEGORY = "ggMPOg1uX0pmQ0s2V0JRclZs"
private const val ROMANCE = "ggMPOg1uX0FzQ2FhZWtUY211"
private const val SAD = "ggMPOg1uX0JLQ0gySWZKZVY1"
private const val SLEEP_CATEGORY = "ggMPOg1uX1MxaFQ3Z0JMZkN4"
private const val WORKOUT = "ggMPOg1uX09LWkhnTjRGRUJh"
