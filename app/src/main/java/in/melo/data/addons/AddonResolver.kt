package `in`.melo.data.addons

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.util.Log
import `in`.melo.data.preferences.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** A playable answer: where to read it from, and what it is. */
data class ResolvedStream(
    val addonId: String,
    val addonName: String,
    val trackId: String,
    val stream: AddonStream,
    /** The runtime of the row the URL came from; the URL itself does not say. */
    val durationSec: Int?,
) {
    val format: StreamFormat get() = StreamFormat.of(stream)
}

/** What an answer is, read from whatever the add-on stated. */
data class StreamFormat(val isLossless: Boolean?, val bitDepth: Int?, val sampleRateHz: Int?, val kbps: Int?) {
    val summary: String
        get() = listOfNotNull(
            when (isLossless) { true -> "lossless"; false -> "lossy"; null -> null },
            bitDepth?.let { "$it-bit" },
            sampleRateHz?.let { "%.1f kHz".format(it / 1000.0) },
            kbps?.let { "$it kbps" },
        ).joinToString(" ").ifEmpty { "unstated" }

    /** Ahead of [other]: lossless first, then bit depth and sample rate, then bitrate. */
    fun beats(other: StreamFormat?): Boolean {
        if (other == null) return true
        if (isLossless != other.isLossless) return isLossless == true
        if (isLossless == true) {
            val depth = (bitDepth ?: 0).compareTo(other.bitDepth ?: 0)
            if (depth != 0) return depth > 0
            val rate = (sampleRateHz ?: 0).compareTo(other.sampleRateHz ?: 0)
            if (rate != 0) return rate > 0
        }
        return (kbps ?: 0) > (other.kbps ?: 0)
    }

    companion object {
        private val LOSSLESS_CODECS = setOf("flac", "alac", "wav", "pcm", "aiff", "wavpack", "ape")
        private val LOSSY_CODECS = setOf("aac", "mp3", "mp4a", "opus", "vorbis", "ogg", "ac3", "eac3", "ec-3", "heaac", "he-aac")
        private val LOSSLESS_WORDS = Regex("""lossless|hi[-_ ]?res|flac|alac|\d{2}[-_ ]?bit""", RegexOption.IGNORE_CASE)
        private val LOSSY_WORDS = Regex("""\b(aac|mp3|opus|vorbis)\b|\d{2,3}\s*kbps""", RegexOption.IGNORE_CASE)

        fun of(stream: AddonStream): StreamFormat {
            val codec = stream.statedCodec
            val lossless = when {
                codec != null && LOSSLESS_CODECS.any { codec.startsWith(it) } -> true
                codec != null && LOSSY_CODECS.any { codec.startsWith(it) } -> false
                LOSSLESS_WORDS.containsMatchIn(stream.qualityText) -> true
                LOSSY_WORDS.containsMatchIn(stream.qualityText) -> false
                else -> null
            }
            return StreamFormat(lossless, stream.bits, stream.sampleRateHz, stream.kbps)
        }
    }
}

/**
 * Finds a queued track in the listener's add-ons ("Direct Source Stream"), searches
 * them for the Search screen, and opens a row picked there.
 *
 * Every call suspends on IO and may take seconds: add-ons resolve upstream.
 */
object AddonResolver {
    private const val TAG = "MeloAddons"

    /** Rows asked for per search: enough to hold the right recording among its covers. */
    private const val MATCH_CANDIDATES = 15
    /** Rows opened per add-on before giving up on it for a track. */
    private const val STREAM_ATTEMPTS = 3
    /** A lossless request still accepts a row within this much of the target's length. */
    private const val SAME_RECORDING_SEC = 3

    /** Which add-on row each track matched, so playing it again costs one stream call. */
    private val matched = ConcurrentHashMap<String, Pair<String, AddonTrack>>()
    /** Tracks no add-on held, kept a while so skipping back and forth does not search again. */
    private val missed = ConcurrentHashMap<String, Long>()
    private const val MISS_TTL_MS = 10 * 60 * 1000L

    private lateinit var app: Context

    fun init(context: Context) { if (!::app.isInitialized) app = context.applicationContext }

    private val store get() = AddonStore.get(app)

    /**
     * The add-ons playback may use right now: enabled, and when an add-on asks for
     * a lossless-capable output, only while one is connected.
     */
    fun playbackAddons(): List<AddonConfig> {
        val all = store.enabled()
        if (all.none { it.needsLosslessOutput }) return all
        val lossless = losslessOutputConnected()
        return all.filter { !it.needsLosslessOutput || lossless }
    }

    /** Whether Direct Source Stream applies at all: switched on, and an add-on to ask. */
    fun directStreamActive(): Boolean =
        ::app.isInitialized && SettingsStore.get(app).settings.value.directSourceStream && playbackAddons().isNotEmpty()

    /**
     * The add-on copy of a queued track, or null when no add-on holds that very
     * recording. Add-ons are asked side by side; the first acceptable answer wins,
     * and answers arriving together are ordered by quality, then by the listener's order.
     */
    internal suspend fun directStream(key: String, target: AddonMatcher.Target): ResolvedStream? = coroutineScope {
        if (target.title.isBlank()) return@coroutineScope null
        val addons = playbackAddons()
        if (addons.isEmpty()) return@coroutineScope null
        missed[key]?.let { at -> if (System.currentTimeMillis() - at < MISS_TTL_MS) return@coroutineScope null else missed.remove(key) }
        val slack = SettingsStore.get(app).settings.value.addonLengthToleranceSec.coerceIn(1, 15)

        // A row matched before is opened directly, without searching again.
        matched[key]?.let { (addonId, row) ->
            val config = addons.firstOrNull { it.id == addonId }
            if (config != null) open(config, row, target, slack)?.let { return@coroutineScope it }
            matched.remove(key)
        }

        val rank = addons.withIndex().associate { (index, config) -> config.id to index }
        val running = addons.map { config -> async { matchAndStream(config, target, slack) } }.toMutableList()
        var best: Pair<ResolvedStream, AddonTrack>? = null
        try {
            while (running.isNotEmpty()) {
                val first = select { running.forEach { call -> call.onAwait { call } } }
                val ready = listOf(first) + running.filter { it !== first && it.isCompleted }
                running -= ready.toSet()
                for (done in ready) {
                    val found = done.await() ?: continue
                    val held = best
                    val wins = held == null || found.first.format.beats(held.first.format) ||
                        (!held.first.format.beats(found.first.format) && rank.getValue(found.first.addonId) < rank.getValue(held.first.addonId))
                    if (wins) best = found
                }
                if (best != null) break
            }
        } finally {
            running.forEach { it.cancel() }
        }
        val (stream, row) = best ?: run {
            missed[key] = System.currentTimeMillis()
            return@coroutineScope null
        }
        matched[key] = stream.addonId to row
        Log.d(TAG, "direct: '${target.title}' from ${stream.addonName} at ${stream.format.summary}")
        stream
    }

    /** Lets [key] be looked for again at once, when the listener asks for it by hand. */
    fun forgetMiss(key: String) {
        missed.remove(key)
    }

    /** How far an add-on copy's length may differ from the track's, in seconds. */
    fun lengthToleranceSec(): Int =
        if (::app.isInitialized) SettingsStore.get(app).settings.value.addonLengthToleranceSec.coerceIn(1, 15) else 5

    /** Forgets what [key] matched, after its stream failed in the player. */
    fun forget(key: String) {
        matched.remove(key)
        missed[key] = System.currentTimeMillis()
    }

    private suspend fun matchAndStream(config: AddonConfig, target: AddonMatcher.Target, slack: Int): Pair<ResolvedStream, AddonTrack>? {
        val client = store.client(config)
        for (query in AddonMatcher.queries(target)) {
            val candidates = attempt(config) {
                client.search(query, AddonClient.TIER_LOSSLESS, title = target.title, artist = target.artist).getOrThrow()
            }?.take(MATCH_CANDIDATES) ?: return null
            // Not played from the first note unless the credits and the runtime agree.
            var matches = AddonMatcher.ranked(candidates, target)
            if (target.artist.isNotBlank()) matches = matches.filter { AddonMatcher.sharesArtist(target.artist, it.artist) }
            if (target.durationSec != null) {
                val (same, other) = matches.partition { AddonMatcher.withinSeconds(it, target, slack) }
                if (other.isNotEmpty()) {
                    Log.d(TAG, "${config.name}: ${other.size} match(es) for '${target.title}' outside ±${slack}s of ${target.durationSec}s")
                }
                matches = same
            }
            if (matches.isEmpty()) continue
            return streamBest(config, matches, target, maxOf(SAME_RECORDING_SEC, slack), slack)
        }
        return null
    }

    /** Hi-res ahead of CD quality ahead of lossy, keeping the match order within each. */
    internal fun preferred(matches: List<AddonTrack>, target: AddonMatcher.Target, sameLengthSec: Int): List<AddonTrack> {
        val sameLength = matches.filter { AddonMatcher.withinSeconds(it, target, sameLengthSec) }
        return sameLength.ifEmpty { matches }
            .filterNot { it.isDolbyAtmos }
            .sortedByDescending { if (it.isHiRes) 2 else if (it.isLossless) 1 else 0 }
    }

    private suspend fun streamBest(
        config: AddonConfig,
        matches: List<AddonTrack>,
        target: AddonMatcher.Target,
        sameLengthSec: Int,
        slack: Int,
    ): Pair<ResolvedStream, AddonTrack>? {
        var settleFor: Pair<ResolvedStream, AddonTrack>? = null
        for (row in preferred(matches, target, sameLengthSec).take(STREAM_ATTEMPTS)) {
            val stream = open(config, row, target, slack) ?: continue
            if (stream.format.isLossless != false) return stream to row
            // A lossy answer is kept in case nothing better comes, the best of them.
            if (settleFor == null || stream.format.beats(settleFor.first.format)) settleFor = stream to row
        }
        return settleFor
    }

    /** Opens one row, refusing protected streams and files too small to hold the recording. */
    private suspend fun open(config: AddonConfig, row: AddonTrack, target: AddonMatcher.Target?, slack: Int): ResolvedStream? {
        val stream = attempt(config) { store.client(config).stream(row.id, AddonClient.TIER_LOSSLESS).getOrThrow() }
            ?.takeIf { it.url.isNotBlank() && it.error.isNullOrBlank() }
            ?: row.streamURL?.takeIf { it.isNotBlank() }?.let { AddonStream(url = it, format = row.format, audioQuality = row.audioQuality) }
            ?: return null
        if (stream.isEncrypted) {
            Log.w(TAG, "${config.name} offered a protected stream for '${row.title}'; refused")
            return null
        }
        if (!stream.url.startsWith("https://") && !stream.url.startsWith("http://")) return null
        val resolved = ResolvedStream(config.id, config.name, row.id, stream, row.durationSec)
        val expected = target?.durationSec ?: row.durationSec
        if (expected != null && stream.transport == StreamTransport.File && cutShort(resolved, expected, slack)) {
            Log.w(TAG, "${config.name} served too little audio for '${row.title}' (a preview or a cut-off file)")
            return null
        }
        return resolved
    }

    /** A row picked from the Add-ons search, opened as it is. */
    suspend fun streamRow(addonId: String, trackId: String): ResolvedStream? {
        val config = store.find(addonId) ?: return null
        val row = AddonTrack(id = trackId)
        return open(config, row, target = null, slack = 0)
    }

    /** Every enabled add-on's rows for [query], in the listener's order of add-ons. */
    suspend fun search(query: String): List<Pair<AddonConfig, AddonTrack>> = coroutineScope {
        store.enabled().map { config ->
            async {
                attempt(config) { store.client(config).search(query, AddonClient.TIER_LOSSLESS).getOrThrow() }
                    .orEmpty().filterNot { it.id.isBlank() || it.title.isBlank() }.map { config to it }
            }
        }.awaitAll().flatten()
    }

    /** Reads the file's size with a one-byte request; only a plain file has one to read. */
    private suspend fun cutShort(stream: ResolvedStream, expectedSec: Int, slack: Int): Boolean {
        val bytes = withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder().url(stream.stream.url).header("Range", "bytes=0-0").get().build()
                probeClient.newCall(request).execute().use { response ->
                    when (response.code) {
                        206 -> response.header("Content-Range")?.substringAfterLast('/')?.trim()?.toLongOrNull()
                        200 -> response.header("Content-Length")?.toLongOrNull()
                        else -> null
                    }?.takeIf { it > 0 }
                }
            }.getOrNull()
        } ?: return false
        return tooShort(bytes, stream.format, expectedSec, slack)
    }

    /**
     * Whether [bytes] cannot hold [expectedSec] of audio even at the lowest bitrate
     * the stated format could have: 200 kbps for lossless, 64 kbps for lossy.
     */
    internal fun tooShort(bytes: Long, format: StreamFormat, expectedSec: Int, slackSec: Int): Boolean {
        if (bytes <= 0 || expectedSec <= 0) return false
        val floorKbps = if (format.isLossless == true) LOSSLESS_FLOOR_KBPS
        else minOf(format.kbps?.takeIf { it > 0 } ?: LOSSY_FLOOR_KBPS, LOSSY_FLOOR_KBPS)
        val mostSec = bytes * 8.0 / (floorKbps * 1000.0)
        return mostSec < expectedSec - slackSec
    }

    private const val LOSSLESS_FLOOR_KBPS = 200
    private const val LOSSY_FLOOR_KBPS = 64

    private val probeClient by lazy { AddonClient.client.newBuilder().callTimeout(4, TimeUnit.SECONDS).build() }

    /** Wired, USB and HDMI outputs carry lossless as it is; Bluetooth re-encodes it. */
    fun losslessOutputConnected(): Boolean {
        val audio = app.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        return audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in LOSSLESS_OUTPUTS }
    }

    private val LOSSLESS_OUTPUTS = setOf(
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_ACCESSORY,
        AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_LINE_ANALOG, AudioDeviceInfo.TYPE_LINE_DIGITAL,
    )

    /** One add-on's failure is that add-on missing the track, never the whole lookup failing. */
    private suspend fun <T> attempt(config: AddonConfig, block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        if (e !is AddonNotFound) Log.w(TAG, "${config.name}: ${e.message}")
        null
    }
}
