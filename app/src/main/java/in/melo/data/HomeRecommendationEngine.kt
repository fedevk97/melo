package `in`.melo.data

import `in`.melo.data.youtube.YTM_ARTIST_PREFIX
import `in`.melo.data.youtube.YTM_PLAYLIST_PREFIX
import `in`.melo.data.youtube.YouTubeAccount
import `in`.melo.data.youtube.YouTubeLibrary
import `in`.melo.data.youtube.YouTubeMusic
import `in`.melo.data.youtube.YouTubePages
import `in`.melo.data.youtube.youTubeVideoId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.util.Locale
import kotlin.random.Random

/** Selects and orders Top Picks using the listener's linked YouTube Music account. */
object HomeRecommendationEngine {
    private const val ARTIST_POOL = 10
    private const val ARTISTS_ON_ROW = 3
    private const val RELATED_ARTISTS_ON_CARD = 2
    private const val SEED_POOL = 40
    private const val SEED_RADIOS = 3
    private const val STATION_FROM_MIX = 30
    private const val STATION_SIZE = 60
    private const val MIN_FILTERED_QUEUE = 12
    private const val MOOD_PLAYLISTS = 3
    private const val MOOD_QUEUE = 60
    private val yearPattern = Regex("\\b(19|20)\\d{2}\\b")

    private data class RankedArtist(val browseId: String, val name: String, val score: Double)
    private enum class MixKind { SUPERMIX, DISCOVER, NEW_RELEASE }

    /** Full ordering: newest release first, with the other recommendation cards reshuffled. */
    suspend fun build(
        history: List<HomeHistoryTrack>,
        mixedForYou: List<Music>,
        local: HomeLocalTaste,
        now: LocalDateTime = LocalDateTime.now(),
    ): List<HomeRecommendationPick> = coroutineScope {
        val musicHistory = history.filterNot(HomeHistoryTrack::isEpisode)
        val resolvedLocalArtists = resolveLocalArtists(local.artists, musicHistory)
        val ranked = weightedSample(rankArtists(musicHistory, resolvedLocalArtists).take(ARTIST_POOL), ARTISTS_ON_ROW)
        val pages = ranked.map { ref ->
            async(Dispatchers.IO) {
                try {
                    YouTubePages.artistRecommendations(ref.browseId)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
            }
        }.awaitAll()
        val artists = ranked.zip(pages).mapNotNull { (rankedArtist, page) ->
            page?.let { if (it.artist.title.isBlank()) it.copy(artist = it.artist.copy(title = rankedArtist.name)) else it }
        }.distinctBy { artistKey(it.artist.title) }
        val mixes = findMixes(mixedForYou)
        val lead = latestRelease(artists)
        val regionHints = regionHints()
        val rest = buildList {
            personalStation(mixes, musicHistory, local)?.let(::add)
            add(moodPick(now, local, regionHints))
            artists.getOrNull(0)?.let(::artistAndSimilar)?.let(::add)
            artists.getOrNull(1)?.let(::featuredArtist)?.let(::add)
            mixes[MixKind.NEW_RELEASE]?.let { mix ->
                add(HomeRecommendationPick(
                    item = mix,
                    type = HomeRecommendationType.NEW_RELEASE_MIX,
                    title = mix.title,
                    subtitle = mix.subtitle,
                    watchTarget = mix.watchTarget(),
                ))
            }
            artists.getOrNull(2)?.let(::artistAndSimilar)?.let(::add)
            discoveryStation(mixes, artists, musicHistory)?.let(::add)
        }.shuffled()
        (listOfNotNull(lead) + rest).distinctBy { it.item.id }
    }

    /** Play a mood, My Supermix, artist queue, or Discovery Station behind a card. */
    suspend fun queue(pick: HomeRecommendationPick, localHistory: List<Music> = emptyList()): List<Music> = when (pick.type) {
        HomeRecommendationType.MOOD -> moodQueue(pick)
        HomeRecommendationType.PERSONAL_STATION,
        HomeRecommendationType.DISCOVERY_STATION -> freshStation(pick, localHistory)
        else -> {
            val target = pick.watchTarget ?: throw YouTubeMusicQueueFailure("This recommendation has no music queue.")
            watchQueue(target, 120)
        }
    }

    /** Current-time mood mix: 70% time-matched, 30% any core mood. */
    fun moodFor(hour: Int): HomeMood {
        val suited = when (hour) {
            in 5..8 -> listOf(HomeMood.ENERGY, HomeMood.FEEL_GOOD, HomeMood.FOCUS)
            in 9..11 -> listOf(HomeMood.FOCUS, HomeMood.FEEL_GOOD, HomeMood.ENERGY)
            in 12..16 -> listOf(HomeMood.FEEL_GOOD, HomeMood.FOCUS, HomeMood.ENERGY, HomeMood.RELAX)
            in 17..19 -> listOf(HomeMood.ENERGY, HomeMood.FEEL_GOOD, HomeMood.RELAX)
            in 20..22 -> listOf(HomeMood.LOVE, HomeMood.RELAX, HomeMood.HEARTBREAK, HomeMood.FEEL_GOOD)
            else -> listOf(HomeMood.RELAX, HomeMood.FEELING_BLUE, HomeMood.HEARTBREAK, HomeMood.LOVE)
        }
        return if (Random.nextFloat() < 0.7f) suited.random() else HomeMood.coreMoods.random()
    }

    private suspend fun resolveLocalArtists(names: List<String>, history: List<HomeHistoryTrack>): List<Music> = coroutineScope {
        val known = history.flatMap(HomeHistoryTrack::artists).associateBy { artistKey(it.title) }
        val semaphore = Semaphore(3)
        names.take(10).map { name ->
            async(Dispatchers.IO) {
                known[artistKey(name)] ?: semaphore.withPermit {
                    try {
                        YouTubePages.searchArtist(name)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        null
                    }
                }
            }
        }.awaitAll().filterNotNull().distinctBy(Music::id)
    }

    /** History counts once with recency weight; Melo's month chart adds a lower positional weight. */
    private fun rankArtists(history: List<HomeHistoryTrack>, local: List<Music>): List<RankedArtist> {
        val scores = LinkedHashMap<String, RankedArtist>()
        fun credit(artist: Music?, weight: Double) {
            artist ?: return
            val browseId = artist.id.removePrefix(YTM_ARTIST_PREFIX)
            if (!browseId.startsWith("UC") || artist.title.isBlank() || artist.title.equals("Various Artists", true)) return
            val key = browseId
            val previous = scores[key]
            scores[key] = RankedArtist(browseId, artist.title, (previous?.score ?: 0.0) + weight)
        }
        history.forEachIndexed { index, row ->
            val recency = 1.0 - index.toDouble() / history.size.coerceAtLeast(1)
            credit(row.primaryArtist ?: row.artists.firstOrNull(), 1.0 + recency * 0.5)
        }
        local.forEachIndexed { index, artist ->
            credit(artist, (local.size - index).toDouble() * 0.5)
        }
        return scores.values.sortedByDescending(RankedArtist::score)
    }

    /** Weighted sampling without replacement keeps top artists common and the rest rotating. */
    private fun <T> weightedSample(ranked: List<T>, count: Int): List<T> {
        val pool = ranked.withIndex().toMutableList()
        val picked = ArrayList<T>(count)
        while (picked.size < count && pool.isNotEmpty()) {
            val weights = pool.map { ranked.size - it.index }.map(Int::toDouble)
            var roll = Random.nextDouble() * weights.sum()
            val index = weights.indexOfFirst { roll -= it; roll <= 0 }.coerceAtLeast(0)
            picked += pool.removeAt(index).value
        }
        return picked
    }

    private fun findMixes(items: List<Music>): Map<MixKind, Music> {
        val mixes = items.filter(::isYouTubeMix)
        if (mixes.isEmpty()) return emptyMap()
        val found = mutableMapOf<MixKind, Music>()
        mixes.forEach { item ->
            val title = item.title.lowercase(Locale.ROOT)
            val kind = when {
                "supermix" in title -> MixKind.SUPERMIX
                "discover" in title || "découverte" in title -> MixKind.DISCOVER
                "new release" in title || "nouveautés" in title -> MixKind.NEW_RELEASE
                else -> null
            }
            if (kind != null) found.putIfAbsent(kind, item)
        }
        if (found.isEmpty()) found[MixKind.SUPERMIX] = mixes.first()
        return found
    }

    private fun isYouTubeMix(item: Music): Boolean = item.kind == MusicKind.Playlist &&
        item.id.removePrefix(YTM_PLAYLIST_PREFIX).removePrefix("VL").startsWith("RDTMAK")

    private fun Music.watchTarget(): YouTubeWatchTarget? = id.takeIf { isYouTubeMix(this) }
        ?.removePrefix(YTM_PLAYLIST_PREFIX)?.removePrefix("VL")?.let(::YouTubeWatchTarget)

    private fun latestRelease(artists: List<HomeArtistRecommendations>): HomeRecommendationPick? {
        data class Candidate(val rank: Int, val release: HomeArtistRelease, val artist: HomeArtistRecommendations, val year: Int)
        val candidates = artists.flatMapIndexed { rank, artist ->
            artist.releases.map { release ->
                Candidate(rank, release, artist, yearPattern.findAll(release.item.subtitle + " " + release.item.note)
                    .lastOrNull()?.value?.toIntOrNull() ?: 0)
            }
        }
        val best = candidates.maxWithOrNull(compareBy<Candidate> { it.year }
            .thenBy { it.release.singleOrEp }.thenByDescending { it.rank }) ?: return null
        return HomeRecommendationPick(
            item = best.release.item,
            type = HomeRecommendationType.LATEST_RELEASE,
            title = best.release.item.title,
            subtitle = best.artist.artist.title,
            artworkUrls = listOfNotNull(best.release.item.imageUrl),
            artistName = best.artist.artist.title,
        )
    }

    private fun personalStation(
        mixes: Map<MixKind, Music>,
        history: List<HomeHistoryTrack>,
        local: HomeLocalTaste,
    ): HomeRecommendationPick? {
        val mix = mixes[MixKind.SUPERMIX]
        val seed = local.songs.firstOrNull { it.youTubeVideoId != null }
            ?: history.firstOrNull { it.track.youTubeVideoId != null }?.track
        val target = mix?.watchTarget() ?: seed?.youTubeVideoId?.let { radioTarget(it) } ?: return null
        val cardItem = mix ?: seed ?: return null
        return HomeRecommendationPick(
            item = cardItem,
            type = HomeRecommendationType.PERSONAL_STATION,
            title = mix?.title.orEmpty(),
            subtitle = mix?.subtitle.orEmpty(),
            artworkUrls = listOfNotNull(mix?.imageUrl ?: cardItem.imageUrl),
            watchTarget = target,
        )
    }

    private fun moodPick(now: LocalDateTime, local: HomeLocalTaste, region: List<String>): HomeRecommendationPick {
        val mood = moodFor(now.hour)
        val title = mood.name.lowercase(Locale.ROOT).replace('_', ' ').replaceFirstChar { it.titlecase(Locale.ROOT) }
        val item = Music("melo:home-mood:${mood.name}", title, "", MusicKind.Playlist)
        return HomeRecommendationPick(
            item = item,
            type = HomeRecommendationType.MOOD,
            title = title,
            mood = mood,
            subtitle = (local.genres.map { it.lowercase(Locale.ROOT) } + region).distinct().joinToString("|"),
        )
    }

    private fun artistAndSimilar(artist: HomeArtistRecommendations): HomeRecommendationPick? {
        val radio = artist.mixTarget ?: return null
        if (artist.artist.imageUrl == null) return null
        val related = artist.relatedArtists.asSequence()
            .filter { artistKey(it.title) != artistKey(artist.artist.title) }
            .filter { it.imageUrl != null }
            .distinctBy { artistKey(it.title) }
            .take(RELATED_ARTISTS_ON_CARD)
            .toList()
        return HomeRecommendationPick(
            item = artist.artist,
            type = HomeRecommendationType.ARTIST_AND_SIMILAR,
            title = artist.artist.title,
            subtitle = related.joinToString(" · ") { it.title },
            artworkUrls = (listOfNotNull(artist.artist.imageUrl) + related.mapNotNull(Music::imageUrl)).distinct().take(3),
            artistName = artist.artist.title,
            artistBrowseId = artist.artist.id.removePrefix(YTM_ARTIST_PREFIX),
            watchTarget = radio,
        )
    }

    private fun featuredArtist(artist: HomeArtistRecommendations): HomeRecommendationPick? {
        val target = artist.shuffleTarget ?: artist.mixTarget ?: return null
        if (artist.artist.imageUrl == null) return null
        return HomeRecommendationPick(
            item = artist.artist,
            type = HomeRecommendationType.FEATURED_ARTIST,
            title = artist.artist.title,
            subtitle = artist.monthlyListeners.orEmpty(),
            artworkUrls = listOfNotNull(artist.artist.imageUrl),
            artistName = artist.artist.title,
            artistBrowseId = artist.artist.id.removePrefix(YTM_ARTIST_PREFIX),
            watchTarget = target,
        )
    }

    private fun discoveryStation(
        mixes: Map<MixKind, Music>, artists: List<HomeArtistRecommendations>, history: List<HomeHistoryTrack>,
    ): HomeRecommendationPick? {
        val mix = mixes[MixKind.DISCOVER]
        if (mix != null) return HomeRecommendationPick(
            item = mix, type = HomeRecommendationType.DISCOVERY_STATION, title = "Discovery Station",
            subtitle = mix.title, artworkUrls = listOfNotNull(mix.imageUrl), watchTarget = mix.watchTarget(),
        )
        val seed = artists.firstOrNull() ?: return null
        val radio = seed.mixTarget ?: return null
        return HomeRecommendationPick(
            item = seed.artist,
            type = HomeRecommendationType.DISCOVERY_STATION,
            title = "Discovery Station",
            subtitle = seed.artist.title,
            artworkUrls = listOfNotNull(seed.artist.imageUrl),
            artistName = seed.artist.title,
            watchTarget = radio,
            excludeVideoIds = history.take(200).mapNotNull { it.track.youTubeVideoId },
        )
    }

    private suspend fun freshStation(pick: HomeRecommendationPick, localHistory: List<Music>): List<Music> = coroutineScope {
        val target = pick.watchTarget ?: throw YouTubeMusicQueueFailure("This recommendation has no station.")
        val mixTask = async(Dispatchers.IO) {
            try {
                watchQueue(target, STATION_SIZE)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emptyList()
            }
        }
        val accountHistory = if (YouTubeAccount.signedIn) async(Dispatchers.IO) {
            try {
                YouTubeLibrary.recentHistoryRows(limit = 2_000).filterNot(HomeHistoryTrack::isEpisode)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emptyList()
            }
        }.await() else emptyList()
        val localYtm = localHistory.filter { it.youTubeVideoId != null }.map { HomeHistoryTrack(it) }
        val history = (accountHistory + localYtm).distinctBy { it.track.id }
        val seeds = history.filter { it.track.youTubeVideoId != null }.take(SEED_POOL).shuffled().take(SEED_RADIOS)
        val radios = seeds.map { seed ->
            async(Dispatchers.IO) {
                val videoId = seed.track.youTubeVideoId ?: return@async emptyList()
                try {
                    watchQueue(radioTarget(videoId), 60)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    emptyList()
                }
            }
        }
        val mix = mixTask.await()
        val around = radios.awaitAll().flatten()
        val discovery = pick.type == HomeRecommendationType.DISCOVERY_STATION
        val heard = if (discovery) history.mapNotNullTo(HashSet()) { it.track.youTubeVideoId } + pick.excludeVideoIds else emptySet()
        val seedArtist = pick.artistName?.takeIf { discovery }?.let(::artistKey)
        fun keep(song: Music): Boolean = song.youTubeVideoId !in heard &&
            (seedArtist == null || !artistKey(song.subtitle).contains(seedArtist))
        val core = mix.filter(::keep).shuffled().take(STATION_FROM_MIX)
        val coreIds = core.mapTo(HashSet(), Music::id)
        val extra = around.filter { keep(it) && it.id !in coreIds }.distinctBy(Music::id)
            .shuffled().take((STATION_SIZE - core.size).coerceAtLeast(0))
        val station = (core + extra).shuffled()
        when {
            station.size >= MIN_FILTERED_QUEUE -> station
            mix.isNotEmpty() -> mix.shuffled()
            else -> throw YouTubeMusicQueueFailure("YouTube Music did not return a station queue.")
        }
    }

    private suspend fun moodQueue(pick: HomeRecommendationPick): List<Music> = coroutineScope {
        val mood = pick.mood ?: throw YouTubeMusicQueueFailure("This mood has no category.")
        val shelves = withContext(Dispatchers.IO) { YouTubePages.moodPlaylistShelves(mood) }
        val hints = regionHints() + pick.subtitle.split('|').filter(String::isNotBlank)
        fun score(shelf: HomeMoodPlaylistShelf): Int {
            val title = shelf.title.lowercase(Locale.ROOT)
            return 2 * mood.shelfWords.count { it in title } + hints.count { it.isNotBlank() && it.lowercase(Locale.ROOT) in title }
        }
        val candidates = shelves.filter { it.playlists.isNotEmpty() }
        val best = candidates.maxOfOrNull(::score) ?: throw YouTubeMusicQueueFailure("YouTube Music has no playlists for this mood.")
        val shelf = candidates.filter { score(it) == best }.random()
        val chosen = shelf.playlists.take(6).shuffled().take(MOOD_PLAYLISTS)
        val queues = chosen.map { playlist ->
            async(Dispatchers.IO) {
                val target = YouTubeWatchTarget(playlist.id.removePrefix(YTM_PLAYLIST_PREFIX))
                try {
                    watchQueue(target, 120, preferAudio = true)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    emptyList()
                }
            }
        }.awaitAll().flatten().distinctBy(Music::id).shuffled().take(MOOD_QUEUE)
        if (queues.isEmpty()) throw YouTubeMusicQueueFailure("YouTube Music returned no tracks for this mood.")
        queues
    }

    private suspend fun watchQueue(
        target: YouTubeWatchTarget,
        limit: Int,
        preferAudio: Boolean = false,
    ): List<Music> = withContext(Dispatchers.IO) {
        if (YouTubeAccount.signedIn) YouTubeLibrary.watchQueue(target, limit, preferAudio)
        else YouTubeMusic.watchQueue(target, limit, preferAudio)
    }

    private fun radioTarget(videoId: String) = YouTubeWatchTarget("RDAMVM$videoId", videoId)

    private fun regionHints(): List<String> = when (Locale.getDefault().country) {
        "IN" -> listOf("bollywood", "indian", "hindi", "punjabi", "desi")
        "PK" -> listOf("pakistani", "urdu", "punjabi")
        "KR" -> listOf("k-pop", "korean")
        "JP" -> listOf("j-pop", "japanese")
        "BR" -> listOf("brazil", "sertanejo", "funk")
        "MX", "ES", "AR", "CO", "CL", "PE" -> listOf("latin", "latino", "reggaeton")
        else -> emptyList()
    }

    private fun artistKey(value: String): String = value.lowercase(Locale.ROOT)
        .replace(Regex("\\b(official|topic|vevo)\\b"), " ")
        .replace(Regex("[^\\p{L}\\p{N}]+"), "")
}

class YouTubeMusicQueueFailure(message: String) : java.io.IOException(message)
