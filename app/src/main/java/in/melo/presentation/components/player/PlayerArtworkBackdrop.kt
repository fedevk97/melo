package `in`.melo.presentation.components.player

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import `in`.melo.presentation.utils.LocalReducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.random.Random

// Modified for Melo on 2026-11-24. Licensed under GPL-3.0; see LICENSE.
// The cover is reduced to a small colour field and blurred once, off the main thread, then
// cached; the screen only scales that texture up, so there is never a live full-screen blur.
private val meshCache = LruCache<String, BackdropTexture>(64)

/** A blurred colour field taken from a cover, with how bright it is on average (0 to 1). */
private class BackdropTexture(val image: ImageBitmap, val luminance: Float)

@Composable
internal fun PlayerArtworkBackdrop(artworkUrl: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val reducedMotion = LocalReducedMotion.current
    var mesh by remember { mutableStateOf(artworkUrl?.let(meshCache::get)) }
    LaunchedEffect(artworkUrl) {
        if (artworkUrl.isNullOrBlank()) {
            mesh = null
            return@LaunchedEffect
        }
        meshCache.get(artworkUrl)?.let { mesh = it; return@LaunchedEffect }
        // Retain the last cover's colors while the new one is being decoded.
        repeat(3) { attempt ->
            if (attempt > 0) delay(800L)
            val result = context.imageLoader.execute(
                ImageRequest.Builder(context).data(artworkUrl).size(120).allowHardware(false).build()
            )
            val drawable = (result as? SuccessResult)?.drawable ?: return@repeat
            val ready = withContext(Dispatchers.Default) {
                artworkMesh(drawable.toBitmap(config = Bitmap.Config.ARGB_8888).asImageBitmap(), artworkUrl.hashCode())
            } ?: return@repeat
            meshCache.put(artworkUrl, ready)
            mesh = ready
            return@LaunchedEffect
        }
    }

    var shown by remember { mutableStateOf(mesh) }
    var incoming by remember { mutableStateOf<BackdropTexture?>(null) }
    val fade = remember { Animatable(0f) }
    LaunchedEffect(mesh, reducedMotion) {
        incoming?.let { shown = it }
        incoming = null
        val next = mesh
        when {
            next == null -> shown = null
            shown == null || reducedMotion -> shown = next
            next !== shown -> {
                incoming = next
                fade.snapTo(0f)
                fade.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
                shown = next
                incoming = null
            }
        }
    }
    Canvas(modifier) {
        drawRect(Color(0xFF121212))
        val bounds = IntSize(size.width.roundToInt().coerceAtLeast(1), size.height.roundToInt().coerceAtLeast(1))
        shown?.let { drawImage(it.image, dstSize = bounds, filterQuality = FilterQuality.Low) }
        incoming?.let { drawImage(it.image, dstSize = bounds, alpha = fade.value, filterQuality = FilterQuality.Low) }
        // The controls stay white, so the scrim follows the cover: a dark cover keeps its
        // colour almost untouched and a bright one is dimmed just enough for white to read.
        val shownLum = shown?.luminance ?: 0f
        val lum = incoming?.let { shownLum + (it.luminance - shownLum) * fade.value } ?: shownLum
        drawRect(Brush.verticalGradient(
            0f to Color.Black.copy(alpha = 0.22f + 0.38f * lum),
            0.35f to Color.Black.copy(alpha = 0.10f + 0.30f * lum),
            0.60f to Color.Black.copy(alpha = 0.22f + 0.38f * lum),
            1f to Color.Black.copy(alpha = 0.34f + 0.40f * lum),
        ))
    }
}

/**
 * The player's colour field for [cover], with its average brightness, for drawing outside Compose:
 * the same texture the player stretches behind its controls, so a shared card matches the screen.
 */
internal fun artworkColourField(cover: Bitmap, seed: Int): Pair<Bitmap, Float>? {
    val fit = SourcePx.toFloat() / maxOf(cover.width, cover.height)
    val source = if (fit < 1f) {
        Bitmap.createScaledBitmap(
            cover,
            (cover.width * fit).roundToInt().coerceAtLeast(1),
            (cover.height * fit).roundToInt().coerceAtLeast(1),
            true,
        )
    } else {
        cover
    }
    return artworkMesh(source.asImageBitmap(), seed)?.let { it.image.asAndroidBitmap() to it.luminance }
}

/** The size the cover is read at, as the player loads it. */
private const val SourcePx = 120
private const val GridCells = 8
private const val TextureSize = 48
private const val BlurRadius = 4
private const val BlurPasses = 3

private fun artworkMesh(source: ImageBitmap, seed: Int): BackdropTexture? {
    val width = source.width
    val height = source.height
    if (width < 1 || height < 1) return null
    val cols = GridCells.coerceAtMost(width)
    val rows = GridCells.coerceAtMost(height)
    val red = LongArray(rows * cols)
    val green = LongArray(rows * cols)
    val blue = LongArray(rows * cols)
    val count = IntArray(rows * cols)
    val line = IntArray(width)
    for (y in 0 until height) {
        source.readPixels(line, startX = 0, startY = y, width = width, height = 1)
        val rowBase = ((height - 1 - y) * rows / height) * cols
        for (x in 0 until width) {
            val cell = rowBase + x * cols / width
            val pixel = line[x]
            red[cell] += (pixel shr 16) and 0xFF
            green[cell] += (pixel shr 8) and 0xFF
            blue[cell] += pixel and 0xFF
            count[cell]++
        }
    }
    val grid = IntArray(rows * cols) { cell ->
        val n = count[cell].coerceAtLeast(1)
        val color = argb((red[cell] / n).toInt(), (green[cell] / n).toInt(), (blue[cell] / n).toInt())
        val hsl = FloatArray(3).also { ColorUtils.colorToHSL(color, it) }
        hsl[1] = (hsl[1] * 1.12f).coerceAtMost(1f)
        hsl[2] = hsl[2].coerceAtLeast(0.045f)
        ColorUtils.HSLToColor(hsl)
    }
    val random = Random(seed)
    val mirror = random.nextBoolean()
    val shift = random.nextInt(cols)
    val rotated = grid.copyOf()
    for (row in 1 until rows) for (x in 0 until cols) {
        val src = if (mirror) cols - 1 - x else x
        rotated[row * cols + x] = grid[row * cols + (src + shift) % cols]
    }
    val n = TextureSize
    val texels = IntArray(n * n)
    for (ty in 0 until n) {
        val fy = (ty + 0.5f) / n * rows - 0.5f
        val y0 = floor(fy).toInt().coerceIn(0, rows - 1)
        val y1 = (y0 + 1).coerceAtMost(rows - 1)
        val wy = smoothstep(fy - y0)
        for (tx in 0 until n) {
            val fx = (tx + 0.5f) / n * cols - 0.5f
            val x0 = floor(fx).toInt().coerceIn(0, cols - 1)
            val x1 = (x0 + 1).coerceAtMost(cols - 1)
            val wx = smoothstep(fx - x0)
            val top = lerpArgb(rotated[y0 * cols + x0], rotated[y0 * cols + x1], wx)
            val bottom = lerpArgb(rotated[y1 * cols + x0], rotated[y1 * cols + x1], wx)
            texels[ty * n + tx] = lerpArgb(top, bottom, wy)
        }
    }
    // Three box passes approximate a gaussian, which melts the grid's seams away.
    repeat(BlurPasses) { boxBlur(texels, n) }
    var luminance = 0.0
    for (texel in texels) luminance += ColorUtils.calculateLuminance(texel)
    val image = Bitmap.createBitmap(texels, n, n, Bitmap.Config.ARGB_8888).asImageBitmap()
    return BackdropTexture(image, (luminance / texels.size).toFloat().coerceIn(0f, 1f))
}

/** One horizontal then one vertical box blur over a square texture, edges clamped. */
internal fun boxBlur(texels: IntArray, size: Int, radius: Int = BlurRadius) {
    val scratch = IntArray(texels.size)
    val span = radius * 2 + 1
    fun pass(from: IntArray, to: IntArray, horizontal: Boolean) {
        for (lane in 0 until size) {
            fun at(i: Int): Int {
                val c = i.coerceIn(0, size - 1)
                return if (horizontal) from[lane * size + c] else from[c * size + lane]
            }
            var r = 0
            var g = 0
            var b = 0
            for (i in -radius..radius) {
                val p = at(i)
                r += (p shr 16) and 0xFF
                g += (p shr 8) and 0xFF
                b += p and 0xFF
            }
            for (i in 0 until size) {
                val out = argb(r / span, g / span, b / span)
                if (horizontal) to[lane * size + i] = out else to[i * size + lane] = out
                val add = at(i + radius + 1)
                val drop = at(i - radius)
                r += ((add shr 16) and 0xFF) - ((drop shr 16) and 0xFF)
                g += ((add shr 8) and 0xFF) - ((drop shr 8) and 0xFF)
                b += (add and 0xFF) - (drop and 0xFF)
            }
        }
    }
    pass(texels, scratch, horizontal = true)
    pass(scratch, texels, horizontal = false)
}

private fun smoothstep(t: Float): Float = t.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }

private fun lerpArgb(from: Int, to: Int, t: Float): Int {
    fun channel(shift: Int): Int {
        val a = (from shr shift) and 0xFF
        val b = (to shr shift) and 0xFF
        return (a + (b - a) * t).roundToInt().coerceIn(0, 255)
    }
    return argb(channel(16), channel(8), channel(0))
}

private fun argb(red: Int, green: Int, blue: Int): Int =
    (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
