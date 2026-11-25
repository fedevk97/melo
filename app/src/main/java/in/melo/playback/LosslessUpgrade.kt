package `in`.melo.playback

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import `in`.melo.data.NetworkMonitor
import `in`.melo.data.addons.AddonResolver
import `in`.melo.data.addons.ResolvedStream
import `in`.melo.data.addons.StreamFormat
import `in`.melo.data.preferences.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Switching a playing track from YouTube Music's copy to an add-on's better one.
 *
 * Playback never waits for this: a track starts on whatever answers fast, and the
 * better copy is looked for while it plays. The switch costs a short break in the
 * audio, so every check here leans towards not switching: the copy has to be the
 * same recording (its length within the listener's tolerance, measured against the
 * length the decoder reports), genuinely better (lossless, or a much higher
 * bitrate), and proved playable by a silent player before it is heard.
 *
 * How the switch reaches the player: the queue item is replaced by the same item
 * with a [MARKER] on its address. The marker makes Media3 build a new source for
 * it, and [MeloMediaSourceFactory] reads the proved copy from [streamFor] instead
 * of asking an add-on again.
 */
internal object LosslessUpgrade {
    const val MARKER = "upgrade"

    /** A lossy copy has to be at least this much better to be worth a break in the audio. */
    const val MIN_GAIN_KBPS = 64
    /** Below this much of the track left, a switch would cost more than it brings. */
    const val MIN_REMAINING_MS = 20_000L
    /** What YouTube Music's Opus usually runs at, when the decoder does not say. */
    const val YOUTUBE_KBPS = 160
    /** A playing length this far from the catalogue's is another cut (a video edit), not the track. */
    const val SEVERE_DRIFT_SEC = 20

    private val mutableSearching = MutableStateFlow<String?>(null)
    /** The track a better copy is being looked for, while that runs. */
    val searching: StateFlow<String?> = mutableSearching.asStateFlow()

    private val mutableOfferable = MutableStateFlow<String?>(null)
    /** The playing track, while it is on YouTube Music's copy and an add-on could do better. */
    val offerable: StateFlow<String?> = mutableOfferable.asStateFlow()

    internal val requests = MutableSharedFlow<String>(extraBufferCapacity = 1)

    /** Looks for a better copy of [mediaId] now, whatever the setting says: the listener asked. */
    fun askByHand(mediaId: String) {
        requests.tryEmit(mediaId)
    }

    /** The proved copy each switched item plays, read (not taken) at every prepare. */
    private val switched = ConcurrentHashMap<String, ResolvedStream>()

    fun streamFor(mediaId: String): ResolvedStream? = switched[mediaId]

    fun isMarked(uri: Uri?): Boolean = uri?.getQueryParameter(MARKER) != null

    // ── The rules, kept free of the player so they can be tested ─────────────

    /** Whether [candidate] is worth a break in the audio over a copy running at [playingKbps]. */
    fun worthSwapping(candidate: StreamFormat, playingKbps: Int?): Boolean {
        if (candidate.isLossless == true) return true
        val gain = (candidate.kbps ?: return false) - (playingKbps ?: YOUTUBE_KBPS)
        return gain >= MIN_GAIN_KBPS
    }

    fun sameRecording(candidateSec: Int?, playingSec: Int?, slackSec: Int): Boolean =
        candidateSec != null && playingSec != null && abs(candidateSec - playingSec) <= slackSec

    /**
     * The length to match a copy against. The decoder's is measured rather than
     * claimed, so it wins, except when it is far from the catalogue's: then YouTube
     * served another cut, and the catalogue's length is the recording wanted.
     */
    fun targetSec(catalogueSec: Int?, playingSec: Int?): Int? = when {
        catalogueSec != null && playingSec != null && abs(catalogueSec - playingSec) > SEVERE_DRIFT_SEC -> catalogueSec
        else -> playingSec ?: catalogueSec
    }

    fun enoughLeft(positionMs: Long, durationMs: Long): Boolean =
        durationMs <= 0 || durationMs - positionMs >= MIN_REMAINING_MS

    fun markedUri(uri: String): String = if ('?' in uri) "$uri&$MARKER=1" else "$uri?$MARKER=1"

    fun unmarkedUri(uri: String): String =
        uri.replace("&$MARKER=1", "").replace("?$MARKER=1&", "?").replace("?$MARKER=1", "")

    // ── State shared with the player below ───────────────────────────────────

    internal fun setSearching(mediaId: String?) { mutableSearching.value = mediaId }
    internal fun setOfferable(mediaId: String?) { mutableOfferable.value = mediaId }
    internal fun switchTo(mediaId: String, stream: ResolvedStream) { switched[mediaId] = stream }
    internal fun withdraw(mediaId: String) { switched.remove(mediaId) }
}

/**
 * Watches the player and makes the switches [LosslessUpgrade] describes. Lives
 * with the playback service; everything it touches on the player runs on Main.
 */
@UnstableApi
internal class LosslessUpgrader(
    private val context: Context,
    private val player: ExoPlayer,
    private val scope: CoroutineScope,
) : Player.Listener, AutoCloseable {
    private val settings = SettingsStore.get(context)
    private val streams = AddonStreamSources()
    private var job: Job? = null
    private var jobFor: String? = null
    private var watch: Job? = null
    /** Tracks that have had their look this play, whatever it found. */
    private val asked = mutableSetOf<String>()
    /** Tracks whose switch broke playback: not switched again this session. */
    private val refused = mutableSetOf<String>()
    /** Copies proved and then not used because the queue moved on, kept for a return. */
    private val shelved = mutableMapOf<String, ResolvedStream>()
    private var collector: Job? = null

    fun start() {
        player.addListener(this)
        collector = scope.launch { LosslessUpgrade.requests.collect(::askedByHand) }
        scope.launch { settings.settings.collect { updateOffer(); consider() } }
        scope.launch { NetworkMonitor.online.collect { updateOffer(); if (it) consider() } }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        val id = mediaItem?.mediaId
        // Switching an item replaces it, which reports a transition to the same track.
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED && id != null && id == jobFor) return
        // A fresh play of a track gets a fresh look; its switched copy belongs to the old play.
        if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED && id != null &&
            !LosslessUpgrade.isMarked(mediaItem.localConfiguration?.uri)) asked -= id
        if (id != jobFor) cancelJob()
        updateOffer()
        consider()
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        updateOffer()
        if (isPlaying) consider()
    }

    override fun onPlayerError(error: PlaybackException) {
        val item = player.currentMediaItem ?: return
        if (!LosslessUpgrade.isMarked(item.localConfiguration?.uri)) return
        Log.w(TAG, "switched copy of ${item.mediaId} failed (${error.errorCodeName}); back to YouTube Music")
        revert(item, player.currentPosition)
    }

    private fun currentIsYouTubeCopy(): MediaItem? {
        val item = player.currentMediaItem ?: return null
        val uri = item.localConfiguration?.uri
        if (!YouTubeUris.isYouTube(uri) || LosslessUpgrade.isMarked(uri)) return null
        // Direct Source Stream already plays this one from an add-on.
        if (AddonPlayback.served.containsKey(item.mediaId)) return null
        return item
    }

    private fun canAsk(): Boolean = NetworkMonitor.online.value && AddonResolver.playbackAddons().isNotEmpty()

    private fun updateOffer() {
        val item = currentIsYouTubeCopy()
        LosslessUpgrade.setOfferable(item?.mediaId?.takeIf { canAsk() && it !in refused })
    }

    private fun consider(byHand: Boolean = false) {
        if (!byHand && (!settings.settings.value.losslessUpgrade || !player.isPlaying)) return
        // Only the Lossless rung asks add-ons on its own; the lower rungs are chosen to save data.
        if (!byHand && `in`.melo.data.AudioQuality.current(context, settings.settings.value) !=
            `in`.melo.data.AudioQuality.LOSSLESS) return
        val item = currentIsYouTubeCopy() ?: return
        val id = item.mediaId
        if (!canAsk() || id in refused || (!byHand && id in asked)) return
        if (job?.isActive == true) {
            if (jobFor == id) return
            cancelJob()
        }
        jobFor = id
        job = scope.launch { lookAndSwitch(item, byHand) }
    }

    private fun askedByHand(mediaId: String) {
        if (player.currentMediaItem?.mediaId != mediaId) return
        asked -= mediaId
        refused -= mediaId
        AddonResolver.forgetMiss(mediaId)
        if (jobFor == mediaId && job?.isActive == true) return
        consider(byHand = true)
    }

    private fun cancelJob() {
        job?.cancel()
        job = null
        jobFor = null
        LosslessUpgrade.setSearching(null)
    }

    private suspend fun lookAndSwitch(item: MediaItem, byHand: Boolean) {
        val id = item.mediaId
        LosslessUpgrade.setSearching(id)
        var answered = false
        try {
            // The decoder's length is the one thing here that is measured rather than claimed.
            val playingMs = withTimeoutOrNull(DURATION_SETTLE_MS) {
                while (true) {
                    val ms = player.takeIf { it.currentMediaItem?.mediaId == id }?.duration ?: return@withTimeoutOrNull null
                    if (ms > 0) return@withTimeoutOrNull ms
                    delay(STEP_MS)
                }
                @Suppress("UNREACHABLE_CODE") null
            }
            val playingSec = playingMs?.let { ((it + 500) / 1000).toInt() }
            val slack = AddonResolver.lengthToleranceSec()
            val target = addonTargetOf(item).let { it.copy(durationSec = LosslessUpgrade.targetSec(it.durationSec, playingSec)) }
            val playingKbps = player.audioFormat?.bitrate?.takeIf { it > 0 }?.let { it / 1000 }

            val found = shelved.remove(id) ?: withContext(Dispatchers.IO) { AddonResolver.directStream(id, target) }
            answered = true
            if (found == null) {
                Log.d(TAG, "no add-on copy of '${target.title}' to switch to")
                return
            }
            if (!LosslessUpgrade.worthSwapping(found.format, playingKbps)) {
                Log.d(TAG, "'${target.title}': ${found.format.summary} is not worth a switch over ${playingKbps ?: "~${LosslessUpgrade.YOUTUBE_KBPS}"} kbps")
                return
            }
            if (found.durationSec != null && !LosslessUpgrade.sameRecording(found.durationSec, target.durationSec, slack)) {
                Log.d(TAG, "'${target.title}': copy runs ${found.durationSec}s against ${target.durationSec}s, outside ±${slack}s")
                return
            }
            val at = player.currentPosition
            if (!LosslessUpgrade.enoughLeft(at, player.duration)) {
                Log.d(TAG, "'${target.title}': too little of the track left to switch")
                return
            }
            val proved = audition(item, found, at, playingMs ?: 0L, slack) ?: return
            // The queue may have moved on while the copy was proved.
            val now = player.currentMediaItem
            if (now?.mediaId != id || LosslessUpgrade.isMarked(now.localConfiguration?.uri)) {
                shelved[id] = found
                Log.d(TAG, "'${target.title}': proved, but the queue moved on; kept for a return")
                return
            }
            if (!LosslessUpgrade.enoughLeft(player.currentPosition, player.duration)) return
            switch(now, found, playingMs ?: 0L, slack, proved)
        } finally {
            // A look cut short by the queue moving on learned nothing; only an answer counts.
            if (answered) asked += id
            if (LosslessUpgrade.searching.value == id) LosslessUpgrade.setSearching(null)
            updateOffer()
        }
    }

    /**
     * Plays the copy silently from [at] until it has proved itself: it opens, its
     * length agrees, a lossless promise decodes as lossless, and it buffers ahead.
     * Returns how far it buffered, or null when it failed any of that.
     */
    private suspend fun audition(item: MediaItem, stream: ResolvedStream, at: Long, playingMs: Long, slackSec: Int): Long? {
        val started = android.os.SystemClock.elapsedRealtime()
        val audition = ExoPlayer.Builder(context).build().apply {
            volume = 0f
            playWhenReady = false
            setMediaSource(streams.create(item, stream))
            seekTo(at)
            prepare()
        }
        try {
            return withTimeoutOrNull(AUDITION_MS) {
                while (true) {
                    audition.playerError?.let {
                        Log.w(TAG, "copy from ${stream.addonName} refused before it was heard: ${it.errorCodeName}")
                        return@withTimeoutOrNull null
                    }
                    if (audition.playbackState == Player.STATE_ENDED) return@withTimeoutOrNull null
                    val length = audition.duration
                    if (audition.playbackState == Player.STATE_READY && length > 0) {
                        if (playingMs > 0 && abs(length - playingMs) > slackSec * 1000L) {
                            Log.w(TAG, "copy from ${stream.addonName} runs ${length}ms against ${playingMs}ms; refused")
                            return@withTimeoutOrNull null
                        }
                        val mime = audition.audioFormat?.sampleMimeType
                        if (mime != null && stream.format.isLossless == true && !isLosslessMime(mime)) {
                            Log.w(TAG, "copy from ${stream.addonName} promised lossless and decoded $mime; refused")
                            return@withTimeoutOrNull null
                        }
                        val wanted = player.currentPosition + PREBUFFER_MS
                        if (audition.bufferedPosition >= wanted || audition.bufferedPercentage >= 100) {
                            Log.d(TAG, "copy from ${stream.addonName} proved in ${android.os.SystemClock.elapsedRealtime() - started}ms")
                            return@withTimeoutOrNull audition.bufferedPosition
                        }
                    }
                    delay(STEP_MS)
                }
                @Suppress("UNREACHABLE_CODE") null
            }.also { if (it == null) Log.d(TAG, "copy from ${stream.addonName} never proved itself") }
        } finally {
            withContext(NonCancellable) { audition.release() }
        }
    }

    private fun switch(item: MediaItem, stream: ResolvedStream, playingMs: Long, slackSec: Int, provedTo: Long) {
        val index = player.currentMediaItemIndex
        val original = item.localConfiguration?.uri?.toString() ?: return
        val position = player.currentPosition
        LosslessUpgrade.switchTo(item.mediaId, stream)
        player.replaceMediaItem(index, item.buildUpon().setUri(LosslessUpgrade.markedUri(original)).build())
        player.seekTo(index, position)
        player.prepare()
        Log.i(TAG, "switched '${item.mediaMetadata.title}' to ${stream.addonName} at ${stream.format.summary}, " +
            "at ${position}ms (proved through ${provedTo}ms)")
        watchSwitch(item.mediaId, position, playingMs, slackSec)
    }

    /** The switched copy has to report the same length once it plays; otherwise it is put back. */
    private fun watchSwitch(mediaId: String, position: Long, playingMs: Long, slackSec: Int) {
        if (playingMs <= 0) return
        watch?.cancel()
        watch = scope.launch {
            val agreed = withTimeoutOrNull(PROVE_MS) {
                while (true) {
                    if (player.currentMediaItem?.mediaId != mediaId) return@withTimeoutOrNull true
                    val now = player.duration
                    if (player.playbackState == Player.STATE_READY && now > 0) {
                        return@withTimeoutOrNull abs(now - playingMs) <= slackSec * 1000L
                    }
                    if (player.playbackState == Player.STATE_ENDED) return@withTimeoutOrNull false
                    delay(STEP_MS)
                }
                @Suppress("UNREACHABLE_CODE") false
            }
            if (agreed == true) return@launch
            val item = player.currentMediaItem?.takeIf { it.mediaId == mediaId } ?: return@launch
            Log.w(TAG, "switched copy of $mediaId reports ${player.duration}ms against ${playingMs}ms; put back")
            revert(item, position)
        }
    }

    private fun revert(item: MediaItem, position: Long) {
        val uri = item.localConfiguration?.uri?.toString() ?: return
        refused += item.mediaId
        LosslessUpgrade.withdraw(item.mediaId)
        AddonPlayback.served.remove(item.mediaId)
        AddonResolver.forget(item.mediaId)
        val index = player.currentMediaItemIndex
        player.replaceMediaItem(index, item.buildUpon().setUri(LosslessUpgrade.unmarkedUri(uri)).build())
        player.seekTo(index, position)
        player.prepare()
        updateOffer()
    }

    override fun close() {
        cancelJob()
        watch?.cancel()
        collector?.cancel()
        player.removeListener(this)
        LosslessUpgrade.setOfferable(null)
    }

    private companion object {
        const val TAG = "MeloUpgrade"
        const val DURATION_SETTLE_MS = 8_000L
        const val STEP_MS = 200L
        const val AUDITION_MS = 25_000L
        const val PREBUFFER_MS = 12_000L
        const val PROVE_MS = 10_000L

        fun isLosslessMime(mime: String) = mime == MimeTypes.AUDIO_FLAC || mime == MimeTypes.AUDIO_ALAC ||
            mime == MimeTypes.AUDIO_RAW || mime == MimeTypes.AUDIO_WAV
    }
}
