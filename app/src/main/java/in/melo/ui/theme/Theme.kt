package `in`.melo.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import `in`.melo.presentation.viewmodel.ColorSchemePair
import androidx.core.graphics.ColorUtils

val LocalMeloDarkTheme = staticCompositionLocalOf { false }
val LocalShowScrollbar = staticCompositionLocalOf { true }

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Suppress("DEPRECATION")
@Composable
fun MeloStatusBarStyle(
    color: Color,
    useDarkIcons: Boolean = ColorUtils.calculateLuminance(color.toArgb()) > 0.55,
    navigationColor: Color? = null,
    useDarkNavigationIcons: Boolean = navigationColor
        ?.let { ColorUtils.calculateLuminance(it.toArgb()) > 0.55 }
        ?: useDarkIcons
) {
    val view = LocalView.current
    if (view.isInEditMode) return

    val updateNavigationBar = navigationColor != null
    SideEffect {
        val window = view.context.findActivity()?.window ?: return@SideEffect
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
        }

        WindowCompat.getInsetsController(window, view).run {
            isAppearanceLightStatusBars = useDarkIcons

            if (updateNavigationBar) {
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = false
                }
                isAppearanceLightNavigationBars = useDarkNavigationIcons
            }
        }
    }
}

// Every Material role is neutral, including fixed roles used by transport controls.
// Explicit roles prevent Material's default lavender palette leaking into sheets.
//
// The greys follow the iOS system ladder Apple Music is drawn in: a black page,
// then #1C1C1E, #2C2C2E and #3A3A3C for each step a surface rises off it, with
// #8E8E93 for second-line text and #38383A hairlines. Light mode mirrors it: a
// white page, #F2F2F7 grouped surfaces, #6E6E73 second-line text.
// The accent stays monochrome (Melo's own identity); it is the primary role, so
// changing it here changes it everywhere.
val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFFAFAFA), onPrimary = Color(0xFF111111),
    primaryContainer = Color(0xFF2C2C2E), onPrimaryContainer = Color(0xFFFAFAFA),
    secondary = Color(0xFFD1D1D6), onSecondary = Color(0xFF111111),
    secondaryContainer = Color(0xFF2C2C2E), onSecondaryContainer = Color(0xFFFAFAFA),
    tertiary = Color(0xFFE5E5EA), onTertiary = Color(0xFF111111),
    tertiaryContainer = Color(0xFF2C2C2E), onTertiaryContainer = Color(0xFFFAFAFA),
    background = Color(0xFF000000), onBackground = Color(0xFFFAFAFA),
    surface = Color(0xFF0D0D0F), onSurface = Color(0xFFFAFAFA),
    surfaceVariant = Color(0xFF1C1C1E), onSurfaceVariant = Color(0xFF8E8E93),
    surfaceContainerLowest = Color(0xFF000000), surfaceContainerLow = Color(0xFF141416),
    surfaceContainer = Color(0xFF1C1C1E), surfaceContainerHigh = Color(0xFF2C2C2E),
    surfaceContainerHighest = Color(0xFF3A3A3C), surfaceBright = Color(0xFF2C2C2E),
    surfaceDim = Color(0xFF0D0D0F), surfaceTint = Color.Transparent,
    outline = Color(0xFF636366), outlineVariant = Color(0xFF38383A),
    inverseSurface = Color(0xFFE5E5EA), inverseOnSurface = Color(0xFF1C1C1E),
    inversePrimary = Color(0xFF2C2C2E),
    error = Color(0xFFFAFAFA), onError = Color(0xFF111111),
    errorContainer = Color(0xFF3A3A3C), onErrorContainer = Color(0xFFFAFAFA),
    primaryFixed = Color(0xFFFAFAFA), primaryFixedDim = Color(0xFFD1D1D6),
    onPrimaryFixed = Color(0xFF111111), onPrimaryFixedVariant = Color(0xFF2C2C2E),
    secondaryFixed = Color(0xFFE5E5EA), secondaryFixedDim = Color(0xFFD1D1D6),
    onSecondaryFixed = Color(0xFF111111), onSecondaryFixedVariant = Color(0xFF2C2C2E),
    tertiaryFixed = Color(0xFFFAFAFA), tertiaryFixedDim = Color(0xFFFAFAFA),
    onTertiaryFixed = Color(0xFF111111), onTertiaryFixedVariant = Color(0xFF2C2C2E),
)

val LightColorScheme = lightColorScheme(
    primary = Color(0xFF111111), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE5E5EA), onPrimaryContainer = Color(0xFF111111),
    secondary = Color(0xFF3A3A3C), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE5E5EA), onSecondaryContainer = Color(0xFF111111),
    tertiary = Color(0xFF2C2C2E), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE5E5EA), onTertiaryContainer = Color(0xFF111111),
    background = Color(0xFFFFFFFF), onBackground = Color(0xFF111111),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF111111),
    surfaceVariant = Color(0xFFF2F2F7), onSurfaceVariant = Color(0xFF6E6E73),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF7F7FA),
    surfaceContainer = Color(0xFFF2F2F7), surfaceContainerHigh = Color(0xFFE5E5EA),
    surfaceContainerHighest = Color(0xFFD1D1D6), surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFE5E5EA), surfaceTint = Color.Transparent,
    outline = Color(0xFF8E8E93), outlineVariant = Color(0xFFD1D1D6),
    inverseSurface = Color(0xFF1C1C1E), inverseOnSurface = Color(0xFFF2F2F7),
    inversePrimary = Color(0xFFF2F2F7),
    error = Color(0xFF2C2C2E), onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFE5E5EA), onErrorContainer = Color(0xFF111111),
    primaryFixed = Color(0xFF111111), primaryFixedDim = Color(0xFF2C2C2E),
    onPrimaryFixed = Color(0xFFFFFFFF), onPrimaryFixedVariant = Color(0xFFE5E5EA),
    secondaryFixed = Color(0xFF2C2C2E), secondaryFixedDim = Color(0xFF3A3A3C),
    onSecondaryFixed = Color(0xFFFFFFFF), onSecondaryFixedVariant = Color(0xFFE5E5EA),
    tertiaryFixed = Color(0xFF111111), tertiaryFixedDim = Color(0xFF111111),
    onTertiaryFixed = Color(0xFFFFFFFF), onTertiaryFixedVariant = Color(0xFFE5E5EA),
)

@Composable
fun MeloTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    colorSchemePairOverride: ColorSchemePair? = null,
    content: @Composable () -> Unit
) {
    // Melo keeps the interface monochrome regardless of wallpaper or album art.
    val finalColorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MeloStatusBarStyle(
        color = finalColorScheme.background,
        navigationColor = finalColorScheme.background
    )

    val reducedMotion = `in`.melo.presentation.utils.observeReducedMotion()
    CompositionLocalProvider(
        LocalMeloDarkTheme provides darkTheme,
        `in`.melo.presentation.utils.LocalReducedMotion provides reducedMotion
    ) {
        MaterialTheme(
            colorScheme = finalColorScheme,
            typography = Typography,
            shapes = Shapes,
            content = content
        )
    }
}
