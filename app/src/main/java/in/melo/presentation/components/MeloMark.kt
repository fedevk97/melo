package `in`.melo.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.util.lerp
import `in`.melo.data.AppIcon
import `in`.melo.presentation.utils.LocalReducedMotion
import `in`.melo.ui.theme.LocalMeloDarkTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

// The M's two peaks as outlines: x runs across the mark from 0 to 1, y down from its top.
private val LeftPeak = floatArrayOf(0.3564f, 0.0000f, 0.3911f, 0.0029f, 0.4185f, 0.0101f, 0.4488f, 0.0245f, 0.4704f, 0.0404f, 0.5238f, 0.0996f, 0.5873f, 0.1977f, 0.5830f, 0.2006f, 0.5382f, 0.1905f, 0.5022f, 0.1934f, 0.4632f, 0.2078f, 0.4315f, 0.2294f, 0.4012f, 0.2583f, 0.3651f, 0.3030f, 0.2742f, 0.4430f, 0.2367f, 0.4921f, 0.1876f, 0.5368f, 0.1443f, 0.5599f, 0.0909f, 0.5758f, 0.0260f, 0.5758f, 0.0087f, 0.5671f, 0.0014f, 0.5570f, 0.0000f, 0.5354f, 0.0029f, 0.5253f, 0.1558f, 0.1558f, 0.1934f, 0.0866f, 0.2280f, 0.0476f, 0.2569f, 0.0274f, 0.3059f, 0.0072f)
private val RightPeak = floatArrayOf(0.7460f, 0.1068f, 0.7864f, 0.1097f, 0.8167f, 0.1212f, 0.8499f, 0.1501f, 0.8629f, 0.1703f, 0.9986f, 0.5339f, 0.9986f, 0.5599f, 0.9885f, 0.5714f, 0.9610f, 0.5786f, 0.8947f, 0.5772f, 0.8355f, 0.5613f, 0.8081f, 0.5469f, 0.7807f, 0.5267f, 0.7504f, 0.4964f, 0.6941f, 0.4257f, 0.6696f, 0.4012f, 0.6479f, 0.3882f, 0.6190f, 0.3838f, 0.5902f, 0.3882f, 0.5556f, 0.4084f, 0.5267f, 0.4343f, 0.4618f, 0.5036f, 0.4055f, 0.5455f, 0.3752f, 0.5599f, 0.3261f, 0.5729f, 0.2828f, 0.5758f, 0.2742f, 0.5714f, 0.3377f, 0.5368f, 0.3709f, 0.5094f, 0.4084f, 0.4704f, 0.4589f, 0.4040f, 0.5743f, 0.2251f, 0.6017f, 0.1905f, 0.6349f, 0.1573f, 0.6638f, 0.1356f, 0.6941f, 0.1198f)
private const val MarkAspect = 0.58f

// The share of the canvas the resting mark fills; the rest is room for the peaks to stretch into.
private const val Headroom = 0.84f
private const val BounceSeconds = 0.92f

/**
 * Melo's M, coloured like the icon on the home screen. It doubles as the pull-to-refresh
 * indicator: the peaks stretch up and a light sweeps across as you pull, they rise and fall in turn
 * like a level meter while a refresh runs, and they spring back to rest with one last gleam.
 */
@Composable
fun MeloMark(
    refreshing: Boolean,
    pullFraction: () -> Float,
    animate: Boolean,
    height: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val context = LocalContext.current
    val icon by remember(context) { AppIcon.live(context) }.collectAsState()
    val dark = LocalMeloDarkTheme.current
    val (top, bottom) = (icon ?: AppIcon.Default).let { if (dark) it.markDark else it.markLight }
    val reduced = LocalReducedMotion.current || !animate

    val clock = remember { mutableFloatStateOf(0f) }
    var bouncing by remember { mutableStateOf(false) }
    val released = remember { floatArrayOf(1f, 1f) }
    val settle = remember { Animatable(1f) }
    val gleam = remember { Animatable(-1f) }

    LaunchedEffect(refreshing, reduced) {
        if (reduced) {
            bouncing = false
            settle.snapTo(1f)
        } else if (refreshing) {
            settle.snapTo(1f)
            bouncing = true
            val start = withFrameNanos { it }
            while (true) {
                clock.floatValue = (withFrameNanos { it } - start) / 1_000_000_000f
            }
        } else if (bouncing) {
            released[0] = peakHeight(clock.floatValue, 0f)
            released[1] = peakHeight(clock.floatValue, PI.toFloat())
            bouncing = false
            settle.snapTo(0f)
            launch {
                gleam.snapTo(0f)
                gleam.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
                gleam.snapTo(-1f)
            }
            settle.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 260f))
        }
    }
    // One gleam whenever the mark comes into view.
    LaunchedEffect(reduced) {
        if (reduced) return@LaunchedEffect
        delay(280)
        gleam.snapTo(0f)
        gleam.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
        gleam.snapTo(-1f)
    }

    val semantics = if (contentDescription != null) {
        Modifier.clearAndSetSemantics { this.contentDescription = contentDescription; heading() }
    } else {
        Modifier.clearAndSetSemantics {}
    }
    Canvas(
        modifier
            .height(height)
            .aspectRatio(Headroom / MarkAspect)
            .then(semantics),
    ) {
        val w = size.width
        val base = size.height
        val pull = if (reduced || bouncing) 0f else pullFraction().coerceIn(0f, 1f)
        val (left, right) = when {
            reduced -> 1f to 1f
            bouncing -> peakHeight(clock.floatValue, 0f) to peakHeight(clock.floatValue, PI.toFloat())
            settle.isRunning -> lerp(released[0], 1f, settle.value) to lerp(released[1], 1f, settle.value)
            else -> (1f + 0.16f * (pull * 1.6f).coerceIn(0f, 1f)) to
                (1f + 0.16f * (pull * 1.6f - 0.6f).coerceIn(0f, 1f))
        }
        val sweep = when {
            gleam.value >= 0f -> gleam.value
            bouncing -> (clock.floatValue / 1.4f) % 1f
            pull > 0f -> pull
            else -> -1f
        }
        val alpha = if (reduced && refreshing) 0.6f else 1f
        val markTop = base - w * MarkAspect
        val fill = Brush.verticalGradient(
            listOf(top.copy(alpha = alpha), bottom.copy(alpha = alpha)),
            startY = markTop,
            endY = base,
        )
        val shine = if (sweep in 0f..1f) {
            val x = lerp(-0.45f, 1.45f, sweep) * w
            Brush.linearGradient(
                0f to Color.Transparent,
                0.5f to Color.White.copy(alpha = if (dark) 0.62f else 0.4f),
                1f to Color.Transparent,
                start = Offset(x - w * 0.22f, base),
                end = Offset(x + w * 0.22f, markTop),
            )
        } else null
        for ((outline, stretch) in listOf(LeftPeak to left, RightPeak to right)) {
            val path = outline.toPath(w, base, stretch)
            drawPath(path, fill)
            if (shine != null) drawPath(path, shine)
        }
    }
}

/** How tall a peak stands mid-refresh: the two alternate, easing in over the first moment. */
private fun peakHeight(seconds: Float, phase: Float): Float {
    val swing = 0.5f + 0.5f * sin(2f * PI.toFloat() * seconds / BounceSeconds + phase)
    return 1f - 0.34f * swing * min(1f, seconds / 0.3f)
}

/**
 * The outline at [width] pixels wide standing on [base], stretched upward by [stretch], smoothed
 * by curving through the midpoints of its corners.
 */
private fun FloatArray.toPath(width: Float, base: Float, stretch: Float): Path {
    val n = size / 2
    fun px(i: Int) = this[(i % n) * 2] * width
    fun py(i: Int) = base - (MarkAspect - this[(i % n) * 2 + 1]) * width * stretch
    return Path().apply {
        moveTo((px(n - 1) + px(0)) / 2f, (py(n - 1) + py(0)) / 2f)
        for (i in 0 until n) {
            quadraticTo(px(i), py(i), (px(i) + px(i + 1)) / 2f, (py(i) + py(i + 1)) / 2f)
        }
        close()
    }
}
