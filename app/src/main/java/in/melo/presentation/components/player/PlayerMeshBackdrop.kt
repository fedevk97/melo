/*
 * Licensed under GPL-3.0; see LICENSE. The player's mesh backdrop on Coil 2 and androidx
 * Palette, with Melo's reduced-motion switch and a palette cache shared with the progress bar.
 */
package `in`.melo.presentation.components.player

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import `in`.melo.presentation.utils.LocalReducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private val FallbackColors = listOf(
    Color(0xFF3A1C71),
    Color(0xFFD76D77),
    Color(0xFF2B5876),
    Color(0xFFFFAF7B),
)

/** The four mesh colours and every colour found in the cover (most common first). */
@Immutable
internal data class MeshPalette(val colors: List<Color>, val art: List<Color> = emptyList())

private val paletteCache = LruCache<String, MeshPalette>(64)

/**
 * The cover's mesh colours, read off a thumbnail. Keeps the last cover's colours while
 * the next one loads, so a skip crossfades instead of flashing the stock colours.
 */
@Composable
internal fun rememberMeshPalette(artworkUrl: String?): MeshPalette {
    val context = LocalContext.current
    var palette by remember { mutableStateOf(artworkUrl?.let(paletteCache::get) ?: MeshPalette(FallbackColors)) }
    LaunchedEffect(artworkUrl) {
        if (artworkUrl.isNullOrBlank()) return@LaunchedEffect
        paletteCache.get(artworkUrl)?.let { palette = it; return@LaunchedEffect }
        repeat(3) { attempt ->
            if (attempt > 0) delay(800L)
            val result = context.imageLoader.execute(
                ImageRequest.Builder(context).data(artworkUrl).size(128).allowHardware(false).build()
            )
            val drawable = (result as? SuccessResult)?.drawable ?: return@repeat
            val ready = withContext(Dispatchers.Default) {
                paletteOf(drawable.toBitmap(config = Bitmap.Config.ARGB_8888))
            }
            paletteCache.put(artworkUrl, ready)
            palette = ready
            return@LaunchedEffect
        }
    }
    return palette
}

/**
 * Four soft colour blobs taken from the cover, blurred into one mesh, with a light scrim
 * so the white controls stay readable. Colours crossfade over 1.4 s on a skip; the blobs
 * drift a little when the song changes and then settle, so a still player redraws nothing.
 */
@Composable
internal fun PlayerMeshBackdrop(
    artworkUrl: String?,
    modifier: Modifier = Modifier,
    blurRadius: Dp = 64.dp,
    driftMillis: Int = 8_000,
) {
    val reduceMotion = LocalReducedMotion.current
    val palette = rememberMeshPalette(artworkUrl)
    val tuned = (palette.colors.ifEmpty { FallbackColors } + FallbackColors).take(4).map { it.tuned() }
    val colorSpec: AnimationSpec<Color> = if (reduceMotion) snap() else tween(1400)
    val animated = tuned.mapIndexed { index, color ->
        animateColorAsState(color, colorSpec, label = "meshColor$index").value
    }
    val baseColor by animateColorAsState(tuned.first().dimmed(), colorSpec, label = "meshBase")

    // Read in draw only, so the drift redraws the canvas without recomposing.
    val phase = remember { Animatable(0f) }
    LaunchedEffect(artworkUrl, reduceMotion) {
        if (reduceMotion) phase.snapTo(0f)
        else phase.animateTo(phase.value + DriftRadians, tween(driftMillis, easing = FastOutSlowInEasing))
    }

    // Scaled up so the blur's clamped edges never show, and clipped from outside the
    // scale so the overhang stays inside the player. The blur needs API 31; below it the
    // radial falloff alone already reads soft.
    Canvas(
        modifier
            .clipToBounds()
            .graphicsLayer { scaleX = 1.3f; scaleY = 1.3f }
            .background(baseColor)
            .blur(blurRadius),
    ) {
        val drift = phase.value
        animated.forEachIndexed { index, color ->
            val anchor = Anchors[index]
            val center = Offset(
                x = (anchor.x + 0.16f * cos(drift * Speeds[index] + index * 1.7f)) * size.width,
                y = (anchor.y + 0.16f * sin(drift * Speeds[index] * 0.9f + index * 2.3f)) * size.height,
            )
            val radius = size.maxDimension * 0.62f
            drawCircle(
                brush = Brush.radialGradient(listOf(color.copy(alpha = 0.85f), color.copy(alpha = 0f)), center, radius),
                radius = radius,
                center = center,
            )
        }
        drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.14f), Color.Black.copy(alpha = 0.42f))))
    }
}

/** The progress bar's colours: a light tint of the song's accent, the accent, a dark shade. */
@Immutable
data class ProgressAccents(val light: Color, val accent: Color, val dark: Color)

/**
 * A bright, saturated accent for the progress bar out of the cover's most colourful
 * swatch, with a light tint and a dark shade of the same hue. A grey cover stays grey.
 * Changes crossfade with the track.
 */
@Composable
internal fun rememberProgressAccents(artworkUrl: String?): ProgressAccents {
    val palette = rememberMeshPalette(artworkUrl)
    val target = remember(palette) {
        val base = (palette.art + palette.colors).map { it.hsl() }.maxByOrNull { it[1] } ?: floatArrayOf(25f, 0.9f, 0.6f)
        val s = if (base[1] < GreySaturation) 0f else base[1].coerceAtLeast(0.6f)
        ProgressAccents(
            light = hslColor(base[0], s * 0.75f, 0.8f),
            accent = hslColor(base[0], s, base[2].coerceIn(0.56f, 0.66f)),
            dark = hslColor(base[0], s, 0.36f),
        )
    }
    val spec = tween<Color>(900, easing = FastOutSlowInEasing)
    return ProgressAccents(
        light = animateColorAsState(target.light, spec, label = "accentLight").value,
        accent = animateColorAsState(target.accent, spec, label = "accentMid").value,
        dark = animateColorAsState(target.dark, spec, label = "accentDark").value,
    )
}

private val Anchors = listOf(Offset(0.20f, 0.25f), Offset(0.80f, 0.20f), Offset(0.75f, 0.80f), Offset(0.25f, 0.75f))
private val Speeds = listOf(1f, -0.7f, 0.85f, -1.15f)

/** A shade under half a turn: enough to see the backdrop react to a skip, short of a full orbit. */
private const val DriftRadians = (PI * 0.45).toFloat()
private const val MeshMinChroma = 0.10f
private const val GreySaturation = 0.08f

private fun paletteOf(bitmap: Bitmap): MeshPalette {
    fun swatches(clearFilters: Boolean): List<Palette.Swatch> =
        Palette.from(bitmap).maximumColorCount(24)
            .let { if (clearFilters) it.clearFilters() else it }
            .generate().swatches.sortedByDescending { it.population }
    val art = swatches(clearFilters = true).map { Color(it.rgb) }
    // The default filter drops near-black and near-white, which on a monochrome cover
    // can be everything there is.
    val found = swatches(clearFilters = false).map { Color(it.rgb) }.ifEmpty { art }
    return MeshPalette(found.meshColors(), art)
}

/**
 * Picks four blobs from the cover's swatches (most common first), colour before white:
 * chromatic swatches ranked by colourfulness against the square root of their share, at
 * most one neutral, and any slots left fanned out from the colours already picked.
 */
private fun List<Color>.meshColors(): List<Color> {
    if (isEmpty()) return FallbackColors
    val (chromatic, neutral) = partition { it.chroma() >= MeshMinChroma }
    if (chromatic.isEmpty()) {
        val distinct = distinctEnough()
        return if (distinct.size >= 4) distinct.take(4) else distinct.expandedToFour()
    }
    val rank = withIndex().associate { (i, c) -> c to i }
    val colourful = chromatic
        .sortedByDescending { it.chroma() * sqrt(1f / (1 + (rank[it] ?: 0))) }
        .distinctEnough()
    if (colourful.size >= 4) return colourful.take(4)
    val picked = (colourful + neutral.take(1)).distinctEnough()
    if (picked.size >= 4) return picked.take(4)
    val out = picked.toMutableList()
    var step = 1
    while (out.size < 4) {
        out += colourful[(step - 1) % colourful.size].shifted(24f * step, 0.12f * step)
        step++
    }
    return out
}

private fun Color.chroma(): Float = maxOf(red, green, blue) - minOf(red, green, blue)

private fun List<Color>.distinctEnough(): List<Color> {
    val kept = mutableListOf<Color>()
    forEach { color -> if (kept.none { it.isCloseTo(color) }) kept += color }
    return kept
}

private fun Color.isCloseTo(other: Color): Boolean {
    val a = hsl()
    val b = other.hsl()
    val hueGap = abs(a[0] - b[0]).let { min(it, 360f - it) }
    return hueGap < 15f && abs(a[2] - b[2]) < 0.12f
}

private fun List<Color>.expandedToFour(): List<Color> {
    val out = toMutableList()
    var step = 1
    while (out.size < 4) {
        out += this[(out.size - size) % size].shifted(24f * step, 0.12f * step)
        step++
    }
    return out
}

private fun Color.shifted(hue: Float, lightness: Float): Color {
    val hsl = hsl()
    hsl[0] = (hsl[0] + hue) % 360f
    hsl[2] = (hsl[2] + lightness).coerceIn(0.2f, 0.7f)
    return Color(ColorUtils.HSLToColor(hsl))
}

private fun Color.hsl(): FloatArray = FloatArray(3).also { ColorUtils.colorToHSL(toArgb(), it) }

private fun hslColor(h: Float, s: Float, l: Float): Color = Color(ColorUtils.HSLToColor(floatArrayOf(h, s, l)))

/** Saturation boosted and lightness clamped, so any cover gives a rich, non-muddy mesh. */
private fun Color.tuned(): Color {
    val hsl = hsl()
    hsl[1] = (hsl[1] * 1.35f).coerceAtMost(1f)
    hsl[2] = hsl[2].coerceIn(0.28f, 0.58f)
    return Color(ColorUtils.HSLToColor(hsl))
}

private fun Color.dimmed(): Color {
    val hsl = hsl()
    hsl[2] = 0.12f
    return Color(ColorUtils.HSLToColor(hsl))
}
