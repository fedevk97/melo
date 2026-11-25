package `in`.melo.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import `in`.melo.R

// Bundled fonts: no network request or Google Play Services needed for typography.
// Bundled SF Pro Display weights, used consistently across Melo.
val MeloSans = FontFamily(
    Font(R.font.sf_pro_display_regular, FontWeight.Normal),
    Font(R.font.sf_pro_display_medium, FontWeight.Medium),
    Font(R.font.sf_pro_display_semibold, FontWeight.SemiBold),
    Font(R.font.sf_pro_display_bold, FontWeight.Bold),
    Font(R.font.sf_pro_display_heavy, FontWeight.ExtraBold),
)

@OptIn(ExperimentalTextApi::class)
val DotMatrixFamily = FontFamily(Font(
    R.font.doto_variable, weight = FontWeight.Bold,
    variationSettings = FontVariation.Settings(
        FontVariation.weight(700), FontVariation.Setting("ROND", 100f)
    )
))

// Compatibility aliases keep existing screens on the same type system.
val GoogleSansRounded = MeloSans
val MontserratFamily = MeloSans

private fun sans(size: Int, height: Int, weight: FontWeight = FontWeight.Normal, spacing: Float = 0f) = TextStyle(
    fontFamily = MeloSans, fontSize = size.sp, lineHeight = height.sp,
    fontWeight = weight, letterSpacing = spacing.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

/**
 * Melo's type scale, after the iOS text styles Apple Music is set in.
 *
 * Big text is bold and drawn slightly tight; small text is drawn at neutral
 * spacing so it stays readable. Each role has one job, so a page title, a
 * section header or a row title is the same size on every screen:
 *
 * - headlineLarge: the large page title (Home, Library, Search).
 * - headlineMedium: a page or sheet title once it is not the large one.
 * - headlineSmall: a section header ("Recently played").
 * - titleLarge: a smaller heading inside a section or card.
 * - titleMedium: a row's or card's title.
 * - titleSmall / labelLarge: emphasised small text and button labels.
 * - bodyLarge / bodyMedium: running text and a row's second line.
 * - bodySmall / labelMedium / labelSmall: captions, metadata and badges.
 */
val Typography = Typography(
    displayLarge = sans(38, 44, FontWeight.Bold, -0.6f),
    displayMedium = sans(34, 41, FontWeight.Bold, -0.4f),
    displaySmall = sans(34, 41, FontWeight.Bold, -0.4f),
    headlineLarge = sans(34, 41, FontWeight.Bold, -0.4f),
    headlineMedium = sans(28, 34, FontWeight.Bold, -0.3f),
    headlineSmall = sans(22, 28, FontWeight.Bold, -0.25f),
    titleLarge = sans(20, 25, FontWeight.Bold, -0.2f),
    titleMedium = sans(17, 22, FontWeight.SemiBold, -0.2f),
    titleSmall = sans(15, 20, FontWeight.SemiBold, -0.1f),
    bodyLarge = sans(16, 22, spacing = -0.1f),
    bodyMedium = sans(14, 19),
    bodySmall = sans(12, 16),
    labelLarge = sans(15, 20, FontWeight.SemiBold, -0.1f),
    labelMedium = sans(13, 18, FontWeight.Medium),
    labelSmall = sans(11, 13, FontWeight.SemiBold, 0.1f),
)

val ExpTitleTypography = Typography.copy(
    displayLarge = Typography.displayLarge,
    displayMedium = Typography.displayMedium,
    titleMedium = Typography.headlineLarge,
)
