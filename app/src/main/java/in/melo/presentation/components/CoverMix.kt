package `in`.melo.presentation.components

import android.graphics.BlendMode
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt
import kotlin.random.Random
import android.graphics.RenderEffect as PlatformRenderEffect

/**
 * Album cover mix: when the track changes on its own or from the transport, the
 * next cover grows through the current one in soft, uneven patches while it
 * comes into focus, instead of the covers sliding past each other.
 */
internal object CoverMixTiming {
    const val DURATION_MS = 2_600

    private const val LINEAR_WEIGHT = 0.25f
    const val BLUR_START_DP = 20f
    const val BLUR_END_PROGRESS = 0.85f
    const val BLUR_STEPS = 64

    const val NOISE_SCALE = 2.6f
    const val NOISE_SOFTNESS = 0.20f
    const val NOISE_LOW = 0.18f
    const val NOISE_HIGH = 0.82f
    const val NOISE_SEED_SPAN = 32f
    const val NOISE_OCTAVES = 3

    fun ease(progress: Float): Float = progress * progress * (3f - 2f * progress)

    /** How much of the next cover shows at [progress] through the mix: eased, with some of the clock kept. */
    fun reveal(progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        val smooth = ease(p)
        return smooth + (p - smooth) * LINEAR_WEIGHT
    }

    /** Which of the [BLUR_STEPS] blur radii the next cover is drawn at; 0 is sharp. */
    fun blurStep(revealed: Float): Int {
        if (revealed <= 0f || revealed >= BLUR_END_PROGRESS) return 0
        val remaining = 1f - revealed / BLUR_END_PROGRESS
        return (remaining * remaining * BLUR_STEPS).roundToInt()
    }
}

// UI thread only. A driver or shader failure is not retried on every change of track.
private var revealFailed = false
private var cachedBlur: BlurSteps? = null

/**
 * Draws the next cover [revealed] of the way in over the one beneath it.
 * [pattern] picks the patches, so each change of track has its own.
 * Below Android 12, with reduced motion, or after a failure, it is a plain fade.
 */
@Composable
internal fun Modifier.coverMixReveal(revealed: () -> Float, pattern: Long, reducedMotion: Boolean): Modifier {
    val plain = graphicsLayer { alpha = revealed() }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || revealFailed || reducedMotion) return plain
    val density = LocalDensity.current.density
    val blur = remember(density) {
        cachedBlur?.takeIf { it.density == density } ?: BlurSteps(density).also { cachedBlur = it }
    }
    val blurLayer = Modifier.graphicsLayer {
        val e = revealed()
        renderEffect = if (revealFailed) null else blur.at(e)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) alpha = e
    }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return then(blurLayer)
    val mask = remember(pattern) { createMask(pattern) } ?: return plain
    return then(mask.modifier(revealed)).then(blurLayer)
}

@RequiresApi(Build.VERSION_CODES.S)
private class BlurSteps(val density: Float) {
    // Immutable effects are safe to share; only the radii actually drawn are made.
    private val effects = arrayOfNulls<RenderEffect>(CoverMixTiming.BLUR_STEPS + 1)

    fun at(revealed: Float): RenderEffect? {
        val step = CoverMixTiming.blurStep(revealed)
        if (step == 0) return null
        return effects[step] ?: runCatching {
            val radius = CoverMixTiming.BLUR_START_DP * density * step / CoverMixTiming.BLUR_STEPS
            PlatformRenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
                .asComposeRenderEffect().also { effects[step] = it }
        }.onFailure { revealFailed = true }.getOrNull()
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun createMask(pattern: Long): PatchMask? = runCatching { PatchMask(pattern) }
    .onFailure { revealFailed = true }.getOrNull()

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class PatchMask(pattern: Long) {
    private val mask = RuntimeShader(PATCH_MASK).apply {
        val random = Random(pattern)
        setFloatUniform(
            "seedOffset",
            random.nextFloat() * CoverMixTiming.NOISE_SEED_SPAN,
            random.nextFloat() * CoverMixTiming.NOISE_SEED_SPAN,
        )
    }
    private val paint = Paint().apply {
        shader = mask
        blendMode = BlendMode.DST_IN
    }
    private var appliedWidth = 0f
    private var appliedHeight = 0f
    private var appliedProgress = -1f

    fun modifier(revealed: () -> Float): Modifier = Modifier.drawWithContent {
        val e = revealed()
        if (e <= 0f) return@drawWithContent
        if (e >= 1f) {
            drawContent()
            return@drawWithContent
        }
        drawIntoCanvas { composeCanvas ->
            val canvas = composeCanvas.nativeCanvas
            val width = size.width
            val height = size.height
            if (width <= 0f || height <= 0f) return@drawIntoCanvas
            val useMask = !revealFailed && canvas.isHardwareAccelerated && runCatching {
                // Each uniform write discards the native shader, so unchanged ones are kept.
                if (width != appliedWidth || height != appliedHeight) {
                    mask.setFloatUniform("bounds", width, height)
                    appliedWidth = width
                    appliedHeight = height
                }
                if (e != appliedProgress) {
                    mask.setFloatUniform("progress", e)
                    appliedProgress = e
                }
                true
            }.getOrElse {
                revealFailed = true
                false
            }
            val layer = if (useMask) canvas.saveLayer(0f, 0f, width, height, null)
            else canvas.saveLayerAlpha(0f, 0f, width, height, (e * 255f).roundToInt())
            try {
                drawContent()
                if (useMask) canvas.drawRect(0f, 0f, width, height, paint)
            } finally {
                canvas.restoreToCount(layer)
            }
        }
    }
}

private val PATCH_MASK = """
    uniform float2 bounds;
    uniform float progress;
    uniform float2 seedOffset;

    float hashCell(float2 cell) {
        // Neighbouring cells must agree on their shared corners: a sine hash breaks
        // along the lattice lines on some GPUs, so small integers are mixed instead.
        float n = mod(cell.x * 127.0 + cell.y * 197.0, 251.0);
        n = mod(n * (n + 73.0) + cell.x * 97.0 + cell.y * 53.0, 251.0);
        n = mod(n * (n + 37.0) + cell.x * 31.0 + cell.y * 109.0, 251.0);
        return n / 251.0;
    }

    float valueNoise(float2 point) {
        float2 cell = floor(point);
        float2 f = fract(point);
        f = f * f * (3.0 - 2.0 * f);
        return mix(mix(hashCell(cell), hashCell(cell + float2(1.0, 0.0)), f.x),
                   mix(hashCell(cell + float2(0.0, 1.0)), hashCell(cell + float2(1.0)), f.x), f.y);
    }

    half4 main(float2 xy) {
        // Broad patches over the whole cover, moved once per change of track.
        float2 point = xy / bounds * ${CoverMixTiming.NOISE_SCALE} + seedOffset;
        float noise = 0.0;
        float weight = 0.5;
        float total = 0.0;
        for (int i = 0; i < ${CoverMixTiming.NOISE_OCTAVES}; ++i) {
            noise += valueNoise(point) * weight;
            total += weight;
            point = point * 2.0 + float2(3.9, 5.1);
            weight *= 0.5;
        }
        float softness = ${CoverMixTiming.NOISE_SOFTNESS};
        float threshold = mix(1.0 + softness, -softness, progress);
        float field = clamp((noise / total - ${CoverMixTiming.NOISE_LOW}) / (${CoverMixTiming.NOISE_HIGH} - ${CoverMixTiming.NOISE_LOW}), 0.0, 1.0);
        return half4(0.0, 0.0, 0.0, smoothstep(threshold - softness, threshold + softness, field));
    }
""".trimIndent()
