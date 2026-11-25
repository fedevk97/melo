/*
 * The app's surface material: Glass, Frost or Solid.
 *
 * Built on the vendored Kyant0/backdrop in `in`.melo.ui.glass.backdrop: one
 * classic glass style, no per-theme option sets. Licensed under GPL-3.0; see LICENSE.
 */
package `in`.melo.ui.glass

import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.runtime.remember
import `in`.melo.ui.glass.backdrop.highlight.HighlightElement
import `in`.melo.ui.glass.backdrop.internal.ShapeProvider
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import `in`.melo.ui.glass.backdrop.backdrops.LayerBackdrop
import `in`.melo.ui.glass.backdrop.drawBackdrop
import `in`.melo.ui.glass.backdrop.effects.blur
import `in`.melo.ui.glass.backdrop.effects.colorControls
import `in`.melo.ui.glass.backdrop.effects.lens
import `in`.melo.ui.glass.backdrop.highlight.Highlight
import `in`.melo.ui.glass.backdrop.shadow.Shadow

enum class MeloSurfaceStyle(val key: String) {
    GLASS("glass"), FROST("frost"), SOLID("solid");

    companion object {
        fun of(key: String?): MeloSurfaceStyle = entries.firstOrNull { it.key == key } ?: GLASS
    }
}

/** What the user picked in Settings → Appearance → App style. */
@Immutable
data class MeloSurfaceOptions(
    val style: MeloSurfaceStyle = MeloSurfaceStyle.GLASS,
    /** 0..1: how much of the theme colour is left off the glass. */
    val transparency: Float = DEFAULT_TRANSPARENCY,
    /** Blur radius in dp, 0..[MAX_BLUR]. */
    val blur: Float = DEFAULT_BLUR,
    /** Saturation of what shows through, 0..[MAX_SATURATION]. */
    val saturation: Float = DEFAULT_SATURATION,
    /** The colour washed into the glass. */
    val tint: MeloGlassTint = MeloGlassTint.SONG,
    /** How far the glass is darkened under the tint (dark theme), 0..1. */
    val darkness: Float = DEFAULT_DARKNESS,
) {
    companion object {
        const val DEFAULT_TRANSPARENCY = 0.9f
        const val DEFAULT_BLUR = 10f
        const val DEFAULT_SATURATION = 0.9f
        const val MAX_BLUR = 32f
        const val MAX_SATURATION = 2f
        const val DEFAULT_DARKNESS = 0.3f
    }
}

/**
 * A colour washed into the glass's fill: the playing song's
 * first, then white and black, then the colours. How much shows is the transparency's to say.
 */
enum class MeloGlassTint(val key: String, private val argb: Long?) {
    SONG("song", null),
    WHITE("white", 0xFFFFFFFF),
    BLACK("black", 0xFF000000),
    BLUE("blue", 0xFF3D7BFF),
    PURPLE("purple", 0xFF9B5CFF),
    PINK("pink", 0xFFFF4F9A),
    ORANGE("orange", 0xFFFF8A3D),
    GREEN("green", 0xFF34C77B),
    ;

    /** The colour itself; [SONG] is the playing song's, and none while nothing plays. */
    @Composable
    @ReadOnlyComposable
    fun color(): Color? = if (argb == null) `in`.melo.ui.theme.LocalNowPlayingTint.current else Color(argb)

    companion object {
        fun of(key: String?): MeloGlassTint = entries.firstOrNull { it.key == key } ?: SONG
    }
}

val LocalMeloSurface = staticCompositionLocalOf { MeloSurfaceOptions() }

/** The pages, recorded so glass surfaces above them can blur and bend them. Null: not recorded. */
val LocalAppBackdrop = compositionLocalOf<LayerBackdrop?> { null }

/**
 * The same recorded pages for menus, alerts and popup cards. Unlike [LocalAppBackdrop] it is
 * not taken away inside the pages: a popup is a window of its own above them, so it can
 * sample them wherever it was opened from. Null: not recorded (Solid, or no blur).
 */
val LocalPopupBackdrop = compositionLocalOf<LayerBackdrop?> { null }

/** Blur needs RenderEffect (Android 12). Below that every style draws as Solid. */
fun isGlassSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * True when the bars float as Glass or Frost over the pages. Pages then drop the dark fade they
 * lay under a solid bar: the material carries the legibility, and the fade would only give it
 * black to blur.
 */
@Composable
@ReadOnlyComposable
fun pagesUnderGlass(): Boolean = LocalMeloSurface.current.style != MeloSurfaceStyle.SOLID && isGlassSupported()

private const val RESOLUTION_SCALE = 0.33f
private const val FROST_BLUR_DP = 24f
private const val LENS_HEIGHT_DP = 24f
private const val LENS_AMOUNT_DP = 24f
private val EDGE_WIDTH = 0.5.dp

@Composable
@ReadOnlyComposable
private fun isDark(dark: Boolean?): Boolean = dark ?: (MaterialTheme.colorScheme.surface.luminance() < 0.5f)

/** The hairline round a surface, so it reads as a sheet laid over the page. */
fun surfaceEdgeColor(dark: Boolean): Color =
    if (dark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.08f)

/**
 * The app's material on [shape]: blurred and refracted glass, a flat frost, or
 * an opaque surface, as the user's app style has it.
 *
 * [carriesText]: text is read straight off it (menus, cards), so the fill is
 * kept dense enough to read whatever is behind. [dark]: the ground is known
 * (the player); null follows the theme.
 */
@Composable
fun Modifier.meloGlass(
    shape: Shape,
    carriesText: Boolean = false,
    dark: Boolean? = null,
    backdrop: LayerBackdrop? = LocalAppBackdrop.current,
    /** Records this surface as drawn, for a lens above it (the tab bar's pill) to refract. */
    exportedBackdrop: LayerBackdrop? = null,
    /**
     * The scrim this surface sits on, if any: a menu's. The glass samples the page under the
     * scrim, so the scrim is laid into its fill too: the menu then
     * reads as the dimmed page seen through glass rather than a clear hole cut in the dimming.
     */
    underlay: Color = Color.Transparent,
): Modifier {
    val options = LocalMeloSurface.current
    val isDark = isDark(dark)
    val edge = surfaceEdgeColor(isDark)
    if (options.style == MeloSurfaceStyle.SOLID || backdrop == null || !isGlassSupported()) {
        val solid = if (dark == null) MaterialTheme.colorScheme.surfaceContainer
            else if (dark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
        return background(solid, shape).border(EDGE_WIDTH, edge, shape)
    }
    val density = LocalDensity.current
    if (options.style == MeloSurfaceStyle.FROST) {
        val fill = frostFill(isDark, carriesText).over(underlay)
        val blurPx = with(density) { FROST_BLUR_DP.dp.toPx() } * RESOLUTION_SCALE
        return drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = { blur(blurPx) },
            highlight = null,
            shadow = null,
            onDrawSurface = { drawRect(fill) },
            exportedBackdrop = exportedBackdrop,
            backdropScale = RESOLUTION_SCALE,
        ).border(EDGE_WIDTH, edge, shape)
    }
    val fill = meloGlassFill(isDark, carriesText).over(underlay)
    val blurPx = with(density) { options.blur.dp.toPx() } * RESOLUTION_SCALE
    val lensHeightPx = with(density) { LENS_HEIGHT_DP.dp.toPx() } * RESOLUTION_SCALE
    val lensAmountPx = with(density) { LENS_AMOUNT_DP.dp.toPx() } * RESOLUTION_SCALE
    val saturation = options.saturation
    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            colorControls(saturation = saturation)
            blur(blurPx)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                lens(lensHeightPx, lensAmountPx, depthEffect = true, chromaticAberration = true)
            }
        },
        highlight = { Highlight.Default },
        shadow = { Shadow.Default },
        onDrawSurface = { drawRect(fill) },
        backdropScale = RESOLUTION_SCALE,
    )
}

/** Frost: a dense, even veil of the theme over a heavy blur. */
fun frostFill(dark: Boolean, carriesText: Boolean = false): Color {
    val base = if (dark) Color(0xFF1C1C1E) else Color(0xFFF7F7F9)
    val alpha = if (dark) 0.65f else 0.6f
    return base.copy(alpha = if (carriesText) alpha + 0.15f else alpha)
}

/**
 * Glass's fill: the theme's near-black or near-white, then
 * the [tint] washed over it and the [darkness] over that, all as dense as the transparency
 * leaves (full strength at [REFERENCE_TRANSPARENCY], nothing at 100%). The tint and the shade
 * are layers of their own, so a faint bar and a dense menu take on the same amount of colour.
 * Where text is read off it the fill never goes under a floor, so labels stay legible.
 */
fun glassFill(
    dark: Boolean,
    transparency: Float,
    carriesText: Boolean = false,
    tint: Color? = null,
    darkness: Float = 0f,
): Color {
    val theme = if (dark) Color(0xFF121212) else Color(0xFFFAFAFA)
    val density = ((1f - transparency) / SURFACE_OPACITY).coerceAtLeast(0f)
    var fill = theme.copy(alpha = (SURFACE_OPACITY * density).coerceIn(0f, 1f))
    if (tint != null) fill = tint.copy(alpha = (TINT_WASH * density).coerceIn(0f, 1f)).compositeOver(fill)
    if (dark && darkness > 0f) {
        fill = Color.Black.copy(alpha = (darkness * MAX_SHADE * density).coerceIn(0f, 1f)).compositeOver(fill)
    }
    if (carriesText) fill = fill.compositeOver(theme.copy(alpha = if (dark) 0.55f else 0.65f))
    return fill
}

/** [glassFill] with the user's glass options: their transparency, tint, darkness and saturation. */
@Composable
@ReadOnlyComposable
fun meloGlassFill(dark: Boolean, carriesText: Boolean = false): Color {
    val options = LocalMeloSurface.current
    // The saturation slider reaches the tint too, as picked at the reference, grey at 0.
    val tint = options.tint.color()?.saturated(options.saturation / REFERENCE_SATURATION)
    return glassFill(dark, options.transparency, carriesText, tint, options.darkness)
}

/**
 * Glass for a control inside the pages, which nothing there can sample: the glass's own fill,
 * light and hairline with no blur or lens. Frost
 * and Solid give their own fill and hairline instead, as the bars over the pages wear them.
 */
@Composable
fun Modifier.meloLightGlass(shape: Shape, carriesText: Boolean = false): Modifier {
    val options = LocalMeloSurface.current
    val dark = isDark(null)
    val edge = surfaceEdgeColor(dark)
    if (options.style == MeloSurfaceStyle.SOLID || !isGlassSupported()) {
        return clip(shape).background(MaterialTheme.colorScheme.surfaceContainer, shape).border(EDGE_WIDTH, edge, shape)
    }
    if (options.style == MeloSurfaceStyle.FROST) {
        return clip(shape).background(frostFill(dark, carriesText), shape).border(EDGE_WIDTH, edge, shape)
    }
    val fill = meloGlassFill(dark, carriesText)
    val provider = remember(shape) { ShapeProvider { shape } }
    return clip(shape)
        .background(fill, shape)
        .then(HighlightElement(shapeProvider = provider, highlight = { Highlight.Default }))
        .border(EDGE_WIDTH, GLASS_EDGE_COLOR, shape)
}

/**
 * A colour laid on glass as more glass, not paint over it: the chosen chip:
 * [fill], the sheen across its top half, the glass light and the
 * rim, all going as [fill]'s alpha does, so a chip fading its fill out is left the glass it was.
 */
@Composable
fun Modifier.glassAccentFill(shape: Shape, fill: Color): Modifier {
    val strength = fill.alpha
    if (strength <= 0f) return this
    val glass = LocalMeloSurface.current.style == MeloSurfaceStyle.GLASS && isGlassSupported()
    val highlight = remember(strength) { Highlight.Default.copy(alpha = strength) }
    val provider = remember(shape) { ShapeProvider { shape } }
    return drawBehind {
        val outline = shape.createOutline(size, layoutDirection, this)
        drawOutline(outline, fill)
        drawOutline(outline, GlassSheen, alpha = strength)
    }
        .then(if (glass) HighlightElement(shapeProvider = provider, highlight = { highlight }) else Modifier)
        .border(EDGE_WIDTH, GLASS_EDGE_COLOR.copy(alpha = GLASS_EDGE_COLOR.alpha * strength), shape)
}

/** [this] with its saturation scaled by [amount] about its own grey. */
internal fun Color.saturated(amount: Float): Color {
    val grey = 0.2126f * red + 0.7152f * green + 0.0722f * blue
    fun channel(c: Float) = (grey + (c - grey) * amount).coerceIn(0f, 1f)
    return Color(channel(red), channel(green), channel(blue), alpha)
}

/** The glass's fill opacity at [REFERENCE_TRANSPARENCY]; the fill fades from it to none at 100%. */
private const val REFERENCE_TRANSPARENCY = 0.6f
private const val SURFACE_OPACITY = 1f - REFERENCE_TRANSPARENCY

/** How strongly the tint is washed over the glass at [REFERENCE_TRANSPARENCY]. */
private const val TINT_WASH = 0.28f

/** How far black the darkness option goes at its top. */
private const val MAX_SHADE = 0.85f

/** The saturation the tint is shown as picked at. */
private const val REFERENCE_SATURATION = 1.5f

/** The glass's hairline wherever the lens's own rim is not drawn. */
private val GLASS_EDGE_COLOR = Color.White.copy(alpha = 0.10f)

/** Ink on the material: plain white or near-black, never a tinted grey. */
@Composable
@ReadOnlyComposable
fun glassContentColor(dark: Boolean? = null): Color =
    if (isDark(dark)) Color.White else Color(0xFF111111)

/** The pill under a selected item on glass. */
@Composable
@ReadOnlyComposable
fun glassIndicatorColor(dark: Boolean? = null): Color =
    if (isDark(dark)) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.08f)

/** The dim laid over a page while a menu or alert sits on it. */
@Composable
@ReadOnlyComposable
fun menuScrimColor(dark: Boolean? = null): Color =
    if (isDark(dark)) Color.Black.copy(alpha = 0.32f) else Color.White.copy(alpha = 0.45f)

/** The soft light along a glass surface's top edge. */
val GlassSheen: Brush = Brush.verticalGradient(
    0f to Color.White.copy(alpha = 0.22f),
    0.35f to Color.White.copy(alpha = 0.04f),
    1f to Color.Transparent,
)

/** A colour laid over [under] as an opaque fill: for solid fallbacks of a translucent one. */
fun Color.over(under: Color): Color = compositeOver(under)
