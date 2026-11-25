package `in`.melo

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import `in`.melo.presentation.MeloMain
import `in`.melo.data.model.Song
import `in`.melo.data.LocalLibrary
import `in`.melo.data.preferences.AppSettings
import `in`.melo.presentation.components.subcomps.LocalTrackQuality
import `in`.melo.ui.theme.LocalShowScrollbar
import `in`.melo.ui.theme.MeloTheme
import `in`.melo.utils.AppLocales

class MainActivity : ComponentActivity() {
    private val pendingSharedLink = MutableStateFlow<String?>(null)

    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(AppLocales.wrap(newBase))

    override fun onStart() {
        super.onStart()
        `in`.melo.update.AppUpdater.get(this).check()
    }

    /**
     * Android 12 and up: keeps the system splash up until the M has risen
     * (drawable/splash_logo), then opens it into the app with [MeloLaunchIris]. With
     * animations off it fades, at once. Held by holding back the
     * first frame. A rotation or a restored activity has nothing to wait for.
     */
    private fun holdSplashForMark(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            // Android 8 to 11: the starting window was the M on black (drawable/
            // launch_background). Plain black again once the app draws, so it never shows through.
            window.decorView.post {
                window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(0xFF0B0B0B.toInt()))
            }
            return
        }
        val animate = android.animation.ValueAnimator.areAnimatorsEnabled()
        splashScreen.setOnExitAnimationListener { view ->
            val decor = window.decorView as? android.view.ViewGroup
            if (decor == null || !animate) {
                view.animate()
                    .alpha(0f)
                    .setDuration(`in`.melo.ui.launch.LaunchSplash.EXIT_FADE_MS)
                    .withEndAction { view.remove() }
                    .start()
            } else {
                `in`.melo.ui.launch.MeloLaunchIris(this).runOver(decor, view.iconView) { view.remove() }
            }
        }
        if (savedInstanceState != null || !animate) return
        val createdAt = android.os.SystemClock.uptimeMillis()
        val content = findViewById<android.view.View>(android.R.id.content)
        content.viewTreeObserver.addOnPreDrawListener(
            object : android.view.ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    val held = android.os.SystemClock.uptimeMillis() - createdAt
                    if (held < `in`.melo.ui.launch.LaunchSplash.ANIMATION_MS) return false
                    content.viewTreeObserver.removeOnPreDrawListener(this)
                    return true
                }
            },
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingSharedLink.value = sharedSpotifyLink(intent)
        enableEdgeToEdge()
        holdSplashForMark(savedInstanceState)
        setContent {
            val model: MeloModel = viewModel()
            val sharedLink by pendingSharedLink.collectAsStateWithLifecycle()
            val settings by model.ui.settings.collectAsStateWithLifecycle()
            val downloads by model.ui.downloadCollections.collectAsStateWithLifecycle()
            val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
            // Asked once, at first launch, so local files are in the library right away.
            val media = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (granted) model.refreshLocalTracks()
            }
            LaunchedEffect(Unit) {
                val preferences = getSharedPreferences("first_launch", MODE_PRIVATE)
                if (!preferences.getBoolean("media_requested", false)) {
                    preferences.edit().putBoolean("media_requested", true).apply()
                    if (ContextCompat.checkSelfPermission(this@MainActivity, LocalLibrary.PERMISSION) != PackageManager.PERMISSION_GRANTED) {
                        media.launch(LocalLibrary.PERMISSION)
                    }
                }
            }
            val downloading = downloads.values.any { it.isActive }
            LaunchedEffect(downloading) {
                if (downloading && Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    val preferences = getSharedPreferences("download_notifications", MODE_PRIVATE)
                    if (!preferences.getBoolean("requested", false)) {
                        preferences.edit().putBoolean("requested", true).apply()
                        notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }
            val dark = when (settings.themeMode) {
                AppSettings.THEME_LIGHT -> false
                AppSettings.THEME_DARK -> true
                else -> isSystemInDarkTheme()
            }
            MeloTheme(darkTheme = dark) {
                val qualityRevision by model.ui.qualityRevision.collectAsStateWithLifecycle()
                val quality = remember(settings.showQualityInLists, qualityRevision) {
                    { song: Song -> if (settings.showQualityInLists) model.ui.qualityOf(song) else null }
                }
                val swipe = remember(settings.swipeRight, settings.swipeLeft) {
                    `in`.melo.presentation.components.TrackSwipeConfig(
                        `in`.melo.presentation.components.TrackSwipeAction.of(settings.swipeRight),
                        `in`.melo.presentation.components.TrackSwipeAction.of(settings.swipeLeft),
                    ) { action, song ->
                        when (action) {
                            `in`.melo.presentation.components.TrackSwipeAction.ADD_TO_QUEUE -> model.ui.addSongToQueue(song)
                            `in`.melo.presentation.components.TrackSwipeAction.PLAY_NEXT -> model.ui.addSongNextToQueue(song)
                            `in`.melo.presentation.components.TrackSwipeAction.LIKE -> model.ui.toggleFavoriteSpecificSong(song)
                            else -> Unit
                        }
                    }
                }
                CompositionLocalProvider(
                    LocalShowScrollbar provides settings.showScrollbar, LocalTrackQuality provides quality,
                    `in`.melo.presentation.components.LocalCompactLists provides settings.compactLists,
                    `in`.melo.presentation.components.LocalSourceBadges provides settings.showSourceBadges,
                    `in`.melo.presentation.components.LocalTrackSwipe provides swipe,
                    `in`.melo.ui.glass.LocalMeloSurface provides `in`.melo.ui.glass.MeloSurfaceOptions(
                        style = `in`.melo.ui.glass.MeloSurfaceStyle.of(settings.surfaceStyle),
                        transparency = settings.glassTransparency,
                        blur = settings.glassBlur,
                        saturation = settings.glassSaturation,
                        tint = `in`.melo.ui.glass.MeloGlassTint.of(settings.glassTint),
                        darkness = settings.glassTintDarkness,
                    ),
                ) {
                    MeloMain(model, sharedLink) { pendingSharedLink.value = null }
                    `in`.melo.update.UpdatePrompt()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingSharedLink.value = sharedSpotifyLink(intent)
    }

    private fun sharedSpotifyLink(intent: Intent?): String? = when (intent?.action) {
        Intent.ACTION_VIEW -> intent.dataString
        Intent.ACTION_SEND -> intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString() ?: intent.dataString
        else -> null
    }
}
