package `in`.melo.playback

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import android.app.PendingIntent
import android.content.Intent
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import `in`.melo.BuildConfig
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.HttpMediaDrmCallback
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import `in`.melo.playback.auto.AutoCallback
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import `in`.melo.MainActivity
import `in`.melo.data.MeloTelemetry
import `in`.melo.data.MusicSource
import `in`.melo.data.musicSourceOf
import `in`.melo.data.UsageAnalytics
import `in`.melo.data.preferences.SettingsStore
import `in`.melo.data.SpotifyHlsDataSource

@UnstableApi
class MeloPlaybackService : MediaLibraryService() {
    private var mediaSession: MediaLibrarySession? = null
    private var source: SpotifyHlsDataSource.Factory? = null
    private var nativeAudio: NativeAudioProvider? = null
    private var behavior: PlaybackBehavior? = null
    private var preloader: QueuePreloader? = null
    private var recovery: PlaybackRecovery? = null
    private var normalizingSink: NormalizingAudioSink? = null
    private val effectsScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate + kotlinx.coroutines.SupervisorJob())
    override fun onCreate() {
        super.onCreate()
        val audio = if (BuildConfig.NATIVE_RUNTIME) NativeAudioProvider(this).also { nativeAudio = it; currentNativeAudio = it } else null
        val http = SpotifyHlsDataSource.Factory(this, nativeAudio = audio != null).also { source = it }
        // Betamax's audio provider (h9v.b in the supplied APK) explicitly selects L3.
        // Configure the platform CDM before any session is opened; it retains all keys.
        val drm = DefaultDrmSessionManager.Builder()
            .setUuidAndExoMediaDrmProvider(C.WIDEVINE_UUID) { uuid ->
                FrameworkMediaDrm.newInstance(uuid).apply { setPropertyString("securityLevel", "L3") }
            }
            .setMultiSession(true).setPlayClearSamplesWithoutKeys(true)
            .build(HttpMediaDrmCallback("https://spclient.wg.spotify.com/widevine-license/v1/audio/license", false, http))
        val renderers = object : DefaultRenderersFactory(this) {
            override fun getCodecAdapterFactory(): androidx.media3.exoplayer.mediacodec.MediaCodecAdapter.Factory {
                val factory = super.getCodecAdapterFactory()
                if (audio != null) return audio.codecFactory
                if (!BuildConfig.DEBUG) return factory
                return androidx.media3.exoplayer.mediacodec.MediaCodecAdapter.Factory { configuration ->
                    val codec = factory.createAdapter(configuration)
                    var count = 0
                    object : androidx.media3.exoplayer.mediacodec.MediaCodecAdapter by codec {
                        override fun queueSecureInputBuffer(index: Int, offset: Int, info: androidx.media3.decoder.CryptoInfo, timeUs: Long, flags: Int) {
                            if (++count % 200 == 1) android.util.Log.i("MeloAudio", "secure mode=${info.mode} ivSize=${info.iv?.size} ivPresent=${info.iv?.any { it.toInt() != 0 }} sub=${info.numSubSamples} clear=${info.numBytesOfClearData?.sum()} encrypted=${info.numBytesOfEncryptedData?.sum()} blocks=${info.encryptedBlocks}/${info.clearBlocks} crypto=${configuration.crypto != null}")
                            codec.queueSecureInputBuffer(index, offset, info, timeUs, flags)
                        }
                    }
                }
            }
            override fun buildAudioSink(context: android.content.Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
                NormalizingAudioSink(DefaultAudioSink.Builder(context).setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .apply { if (BuildConfig.BUILD_TYPE == "debug") setAudioProcessors(arrayOf(SignalProbe())) }.build())
                    .also { normalizingSink = it }
        }
        // Playlists have no length and stay out of both caches: the data source keeps those itself.
        val cacheFlags = CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR or CacheDataSource.FLAG_IGNORE_CACHE_FOR_UNSET_LENGTH_REQUESTS
        val recentAudioWriter = CacheDataSource.Factory().setCache(AudioCache.cache(this)).setCacheKeyFactory(AudioCache.keys)
            .setUpstreamDataSourceFactory(http).setFlags(cacheFlags)
        val recentAudioReader = CacheDataSource.Factory().setCache(AudioCache.cache(this)).setCacheKeyFactory(AudioCache.keys)
            .setUpstreamDataSourceFactory(http).setCacheWriteDataSinkFactory(null).setFlags(cacheFlags)
        val recentAudio = RecentAudioDataSourceFactory(this, recentAudioWriter, recentAudioReader)
        // YouTube Music requests have no known length: they use their own chain, keyed by track.
        val youtube = youTubeCacheChain(this)
        val player = ExoPlayer.Builder(this, renderers)
            .setMediaSourceFactory(MeloMediaSourceFactory(HlsMediaSource.Factory(
                CacheDataSource.Factory().setCache(DownloadCenter.cache(this)).setCacheKeyFactory(AudioCache.keys)
                    .setUpstreamDataSourceFactory(recentAudio)
                    .setCacheWriteDataSinkFactory(null)
                    .setFlags(cacheFlags)
            ).setLoadErrorHandlingPolicy(ResilientLoadPolicy()).apply {
                if (audio != null) setExtractorFactory(NativeHlsExtractor(audio))
                else { setDrmSessionManagerProvider { drm }; if (BuildConfig.DEBUG) setExtractorFactory(ExtractorProbe()) }
            }, ProgressiveMediaSource.Factory(DefaultDataSource.Factory(this)),
                ProgressiveMediaSource.Factory(youtube.kept).setLoadErrorHandlingPolicy(ResilientLoadPolicy())))
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(true).setWakeMode(C.WAKE_MODE_NETWORK).build()
        fun updatePlaybackPresence() {
            MeloTelemetry.setPlaybackActive(player.isPlaying)
        }
        var lastTelemetryTrack: String? = null
        var bufferingStartedAt: Long? = null
        var listeningStartedAt: Long? = null
        var listeningCatalog = "other"
        fun telemetryCatalog(): String = when (musicSourceOf(player.currentMediaItem?.mediaId.orEmpty())) {
            MusicSource.Spotify -> "spotify_catalog"
            MusicSource.YouTubeMusic -> "youtube_music"
            MusicSource.Local -> "local"
        }
        fun recordTrackStart() {
            val id = player.currentMediaItem?.mediaId ?: return
            if (lastTelemetryTrack != id) {
                lastTelemetryTrack = id
                UsageAnalytics.playbackStarted(this, telemetryCatalog())
            }
        }
        fun finishListeningSegment(restart: Boolean = false) {
            val startedAt = listeningStartedAt
            val now = SystemClock.elapsedRealtime()
            if (startedAt != null) UsageAnalytics.playerPlayingTime(this, now - startedAt, listeningCatalog)
            listeningStartedAt = if (restart && player.isPlaying) now else null
            if (listeningStartedAt != null) listeningCatalog = telemetryCatalog()
        }
        fun finishBuffering() {
            val startedAt = bufferingStartedAt ?: return
            bufferingStartedAt = null
            UsageAnalytics.buffering(this, SystemClock.elapsedRealtime() - startedAt, telemetryCatalog())
        }
        fun errorCategory(error: PlaybackException): String = when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_TIMEOUT -> "network"
            PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED,
            PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED,
            PlaybackException.ERROR_CODE_DRM_DISALLOWED_OPERATION -> "authorization"
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
            PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED -> "decoder"
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "unavailable"
            else -> "other"
        }
        player.addListener(object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    recordTrackStart()
                    if (listeningStartedAt == null) {
                        listeningCatalog = telemetryCatalog()
                        listeningStartedAt = SystemClock.elapsedRealtime()
                    }
                } else {
                    finishListeningSegment()
                }
                updatePlaybackPresence()
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == androidx.media3.common.Player.STATE_BUFFERING) {
                    if (bufferingStartedAt == null) bufferingStartedAt = SystemClock.elapsedRealtime()
                } else {
                    finishBuffering()
                }
                updatePlaybackPresence()
            }
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                finishBuffering()
                lastTelemetryTrack = null
                if (player.isPlaying) {
                    finishListeningSegment(restart = true)
                    recordTrackStart()
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                finishBuffering()
                UsageAnalytics.playbackError(this@MeloPlaybackService, telemetryCatalog(), errorCategory(error))
                finishListeningSegment()
                updatePlaybackPresence()
            }
        })
        effectsScope.launch {
            while (kotlinx.coroutines.currentCoroutineContext().isActive) {
                kotlinx.coroutines.delay(60_000)
                if (player.isPlaying && listeningStartedAt != null) finishListeningSegment(restart = true)
            }
        }
        player.preloadConfiguration = if (SettingsStore.get(this).settings.value.preloadUpcomingTracks) {
            ExoPlayer.PreloadConfiguration(NEXT_TRACK_PRELOAD_US)
        } else ExoPlayer.PreloadConfiguration.DEFAULT
        player.addAnalyticsListener(PlaybackDiagnostics)
        preloader = QueuePreloader(this, player, http, recentAudioWriter, effectsScope, audio, youtube.recent).also { it.start() }
        recovery = PlaybackRecovery(this, player, effectsScope, audio).also { it.start() }
        behavior = PlaybackBehavior(this, player).also { it.start() }
        EqualizerRuntime.start(this, effectsScope)
        EqualizerRuntime.setAudioSessionId(player.audioSessionId)
        player.addListener(object : androidx.media3.common.Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) = EqualizerRuntime.setAudioSessionId(audioSessionId)
            // The first track's loudness is only known once its manifest is read, hence READY too.
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) = updateNormalization(player)
            override fun onPlaybackStateChanged(playbackState: Int) = updateNormalization(player)
        })
        val settings = SettingsStore.get(this)
        effectsScope.launch {
            settings.settings.map { it.audioNormalization to it.preloadUpcomingTracks }.distinctUntilChanged()
                .collect { (_, preload) ->
                    updateNormalization(player)
                    player.preloadConfiguration = if (preload) ExoPlayer.PreloadConfiguration(NEXT_TRACK_PRELOAD_US)
                    else ExoPlayer.PreloadConfiguration.DEFAULT
                }
        }
        val intent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        mediaSession = MediaLibrarySession.Builder(this, player, AutoCallback(this, effectsScope)).setSessionActivity(intent).build()
    }
    private fun updateNormalization(player: androidx.media3.common.Player) {
        val sink = normalizingSink ?: return
        val enabled = SettingsStore.get(this).settings.value.audioNormalization
        val id = player.currentMediaItem?.mediaId
        val gain = if (!enabled || id == null) null else TrackLoudness.gainDb(this, id)
        val factor = if (gain == null) 1f else TrackLoudness.linear(gain)
        if (factor != sink.factor) android.util.Log.i("MeloAudio", "normalization enabled=$enabled gain=${gain?.let { "%.1f".format(it) }}dB volume=${"%.2f".format(factor)}")
        sink.factor = factor
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaSession
    override fun onTaskRemoved(rootIntent: Intent?) {
        val keepPlaying = SettingsStore.get(this).settings.value.keepPlayingInBackground
        if (!keepPlaying) mediaSession?.player?.pause()
        if (!keepPlaying || mediaSession?.player?.playWhenReady != true) stopSelf()
    }
    override fun onDestroy() {
        MeloTelemetry.setPlaybackActive(false)
        behavior?.close(); behavior = null
        preloader?.close(); preloader = null
        recovery?.close(); recovery = null
        effectsScope.cancel()
        EqualizerRuntime.setAudioSessionId(0)
        nativeAudio?.cancelPending()
        mediaSession?.run { player.release(); release() }; mediaSession = null
        nativeAudio?.close(); nativeAudio = null
        source?.close(); source = null
        super.onDestroy()
    }

    internal class YouTubeChain(val recent: CacheDataSource.Factory, val kept: CacheDataSource.Factory)

    internal companion object {
        /** Downloads first, then the recent cache, then the network. Nothing is written to downloads while listening. */
        fun youTubeCacheChain(context: android.content.Context): YouTubeChain {
            val youtube = YouTubeDataSource.Factory(context)
            val flags = CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR
            val recent = CacheDataSource.Factory().setCache(AudioCache.cache(context)).setCacheKeyFactory(AudioCache.keys)
                .setUpstreamDataSourceFactory(youtube).setFlags(flags)
            val recentReader = CacheDataSource.Factory().setCache(AudioCache.cache(context)).setCacheKeyFactory(AudioCache.keys)
                .setUpstreamDataSourceFactory(youtube).setCacheWriteDataSinkFactory(null).setFlags(flags)
            val recentPlayback = RecentAudioDataSourceFactory(context, recent, recentReader)
            val kept = CacheDataSource.Factory().setCache(DownloadCenter.cache(context)).setCacheKeyFactory(AudioCache.keys)
                .setUpstreamDataSourceFactory(recentPlayback).setCacheWriteDataSinkFactory(null).setFlags(flags)
            return YouTubeChain(recent, kept)
        }
        private const val NEXT_TRACK_PRELOAD_US = 30_000_000L
        /** Instrumented tests only. */
        @Volatile internal var currentNativeAudio: NativeAudioProvider? = null
    }
}
