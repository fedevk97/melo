/*
 * Licensed under GPL-3.0; see LICENSE.
 */
package `in`.melo.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import `in`.melo.data.preferences.AppSettings
import `in`.melo.presentation.components.player.ProgressAccents
import `in`.melo.presentation.utils.LocalReducedMotion
import `in`.melo.ui.theme.MeloMotion
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.roundToInt

/**
 * The player's position bar, drawn the way iOS draws it: a thin rounded track with no
 * thumb, which swells while a finger is on it. Touching anywhere on it seeks there and
 * dragging follows the finger; [onValueCommit] fires once when the finger lifts.
 *
 * Between position updates the fill glides along on the frame clock (read only in the
 * draw phase, so playback never recomposes the bar) and snaps on big jumps such as a
 * track change.
 *
 * [style] picks how it is drawn (see [AppSettings.PROGRESS_PLAIN]): plain, a glow that
 * runs from the song's [accents] into white with a soft light trailing the playhead, or
 * waves of those colours rising out of the played part. Glow and Wave move only while
 * [isPlaying], and stand still under reduced motion.
 */
@Composable
fun MeloScrubber(
    value: () -> Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    onValueCommit: ((Float) -> Unit)? = null,
    activeTrackColor: Color = MaterialTheme.colorScheme.onSurface,
    inactiveTrackColor: Color = activeTrackColor.copy(alpha = 0.25f),
    isVisible: Boolean = true,
    trackHeight: Dp = ScrubberTrackHeight,
    activeTrackHeight: Dp = ScrubberActiveTrackHeight,
    trackEdgePadding: Dp = 0.dp,
    semanticsLabel: String? = null,
    semanticsProgressStep: Float = 0.01f,
    style: String = AppSettings.PROGRESS_PLAIN,
    isPlaying: Boolean = false,
    accents: ProgressAccents? = null,
) {
    val latestOnValueChange by rememberUpdatedState(onValueChange)
    val latestOnValueCommit by rememberUpdatedState(onValueCommit)
    val latestInteractionSource by rememberUpdatedState(interactionSource)
    var touching by remember { mutableStateOf(false) }
    val swell by animateFloatAsState(if (touching) 1f else 0f, MeloMotion.press(), label = "scrubberSwell")
    val reduceMotion = LocalReducedMotion.current
    val glow = style == AppSettings.PROGRESS_GLOW && accents != null
    val wave = style == AppSettings.PROGRESS_WAVE && accents != null
    val styleMotion = rememberStyleMotion(glow, wave, touching, isPlaying, !reduceMotion)
    val wavePath = remember { Path() }

    val target = remember { derivedStateOf { value().coerceIn(0f, 1f) } }
    val step = semanticsProgressStep.coerceIn(0.005f, 0.25f)
    val semanticValue = remember(step) {
        derivedStateOf { ((target.value / step).roundToInt() * step).coerceIn(0f, 1f) }
    }

    // Playback reports the position a few times a second; tween between reports so the
    // fill moves continuously, but snap when the jump can't be ordinary playback.
    val rendered = remember { mutableFloatStateOf(target.value) }
    var lastUpdateNanos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(touching, enabled) {
        snapshotFlow { target.value }.collect { next ->
            val start = rendered.floatValue
            val now = System.nanoTime()
            if (!enabled || touching || abs(start - next) > 0.1f || abs(start - next) <= 0.0001f) {
                rendered.floatValue = next
                lastUpdateNanos = now
                return@collect
            }
            val intervalMs = if (lastUpdateNanos == 0L) 180L
            else ((now - lastUpdateNanos) / 1_000_000L).coerceIn(1L, 250L)
            lastUpdateNanos = now
            val durationNanos = (intervalMs * 900_000L).coerceAtLeast(1_000_000L)
            var firstFrame = 0L
            while (isActive) {
                val frame = withFrameNanos { it }
                if (firstFrame == 0L) firstFrame = frame
                val t = ((frame - firstFrame).toDouble() / durationNanos).toFloat().coerceIn(0f, 1f)
                rendered.floatValue = start + (next - start) * t
                if (t >= 1f) break
            }
            rendered.floatValue = next
        }
    }

    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(
        modifier
            .fillMaxWidth()
            .height(ScrubberTouchHeight)
            .clearAndSetSemantics {
                if (!semanticsLabel.isNullOrBlank()) contentDescription = semanticsLabel
                progressBarRangeInfo = ProgressBarRangeInfo(semanticValue.value, 0f..1f, steps = 0)
                if (enabled) setProgress { requested ->
                    val coerced = requested.coerceIn(0f, 1f)
                    latestOnValueChange(coerced)
                    latestOnValueCommit?.invoke(coerced)
                    true
                }
            }
            .pointerInput(enabled, trackEdgePadding, rtl) {
                if (!enabled) return@pointerInput
                val edge = trackEdgePadding.toPx()
                fun valueForX(x: Float): Float {
                    val inset = edge.coerceIn(0f, size.width / 2f)
                    val width = (size.width - inset * 2f).coerceAtLeast(1f)
                    val fraction = ((x - inset) / width).coerceIn(0f, 1f)
                    return if (rtl) 1f - fraction else fraction
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    touching = true
                    val drag = DragInteraction.Start()
                    latestInteractionSource?.tryEmit(drag)
                    var latest = valueForX(down.position.x)
                    latestOnValueChange(latest)
                    var pointerId = down.id
                    var finished = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId }
                                ?: event.changes.firstOrNull { it.pressed }
                                ?: break
                            pointerId = change.id
                            if (!change.pressed) {
                                change.consume()
                                finished = true
                                break
                            }
                            if (change.position != change.previousPosition) {
                                change.consume()
                                latest = valueForX(change.position.x)
                                latestOnValueChange(latest)
                            }
                        }
                    } finally {
                        touching = false
                        latestInteractionSource?.tryEmit(
                            if (finished) DragInteraction.Stop(drag) else DragInteraction.Cancel(drag)
                        )
                    }
                    latestOnValueCommit?.invoke(latest)
                }
            }
    ) {
        if (!isVisible) return@Canvas
        val inset = trackEdgePadding.toPx().coerceIn(0f, size.width / 2f)
        val width = (size.width - inset * 2f).coerceAtLeast(0f)
        val position = rendered.floatValue.coerceIn(0f, 1f)
        if (glow || wave) {
            // Drawn left to right from the track's start, mirrored for right-to-left.
            translate(left = inset) {
                scale(scaleX = if (rtl) -1f else 1f, scaleY = 1f, pivot = Offset(width / 2f, size.height / 2f)) {
                    if (glow) drawGlow(width, position, styleMotion, accents!!, inactiveTrackColor)
                    else drawWaveStyle(width, position, swell, styleMotion, accents!!, activeTrackColor, inactiveTrackColor, wavePath)
                }
            }
            return@Canvas
        }
        val height = trackHeight.toPx() + (activeTrackHeight.toPx() - trackHeight.toPx()) * swell
        val top = (size.height - height) / 2f
        val radius = CornerRadius(height / 2f)
        drawRoundRect(inactiveTrackColor, Offset(inset, top), Size(width, height), radius)
        val filled = width * position
        if (filled > 0f) {
            // The fill is the whole rounded track cut off at the position, so its leading
            // end stays square and a sliver of progress never turns into a dot.
            val left = if (rtl) inset + width - filled else inset
            clipRect(left = left, right = left + filled) {
                drawRoundRect(activeTrackColor, Offset(inset, top), Size(width, height), radius)
            }
        }
    }
}

/**
 * What the Glow and Wave styles animate, read only in draw. [flare] is the glow's light
 * (out while held or paused), [held] how far a finger has the bar, [lit] the white end
 * lit by the music, [breath] its slow swell; [time] and [waveSwell] drive the waves,
 * whose clock runs only while the music does.
 */
private class StyleMotion {
    val flare = Animatable(0f)
    val held = Animatable(0f)
    val lit = Animatable(0f)
    val breath = Animatable(1f)
    val waveSwell = Animatable(0f)
    val time = mutableFloatStateOf(0f)
}

@Composable
private fun rememberStyleMotion(glow: Boolean, wave: Boolean, touching: Boolean, playing: Boolean, animate: Boolean): StyleMotion {
    val motion = remember { StyleMotion() }
    LaunchedEffect(glow, touching, playing) {
        if (!glow) return@LaunchedEffect
        motion.flare.animateTo(
            if (touching || !playing) 0f else 1f,
            tween(if (touching) 160 else if (!playing) 700 else 420, easing = FastOutSlowInEasing),
        )
    }
    LaunchedEffect(glow, touching) {
        if (glow) motion.held.animateTo(if (touching) 1f else 0f, tween(220, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(glow, touching, playing) {
        if (glow) motion.lit.animateTo(if (playing || touching) 1f else 0f, tween(700, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(glow, playing, animate) {
        if (glow && playing && animate) {
            motion.breath.animateTo(GlowBreathLow, infiniteRepeatable(tween(GlowBreathMs, easing = FastOutSlowInEasing), RepeatMode.Reverse))
        } else {
            motion.breath.animateTo(1f, tween(400))
        }
    }
    LaunchedEffect(wave, playing, animate) {
        if (!wave || !playing) {
            motion.waveSwell.animateTo(0f, tween(WaveSwellMs, easing = FastOutSlowInEasing))
            return@LaunchedEffect
        }
        if (!animate) {
            // Reduced motion: the waves stand still, at full height.
            motion.waveSwell.snapTo(1f)
            return@LaunchedEffect
        }
        var last = System.nanoTime()
        while (isActive) {
            val dt = withFrameNanos {
                val now = System.nanoTime()
                val elapsed = (now - last).coerceIn(0L, 100_000_000L) / 1_000_000_000f
                last = now
                elapsed
            }
            // Wrapped well past every layer's period, so float precision never wears down.
            motion.time.floatValue = (motion.time.floatValue + dt) % 3600f
            if (motion.waveSwell.value < 1f) {
                motion.waveSwell.snapTo(min(1f, motion.waveSwell.value + dt * 1000f / WaveSwellMs))
            }
        }
    }
    return motion
}

/**
 * The Glow style: a solid pill whose played part runs from a pale tint of the song's
 * accent into white at the playhead, under a soft cone of light trailing back from it.
 */
private fun DrawScope.drawGlow(width: Float, position: Float, motion: StyleMotion, accents: ProgressAccents, inactive: Color) {
    val held = motion.held.value
    val line = GlowLineIdle.toPx() + (GlowLineActive.toPx() - GlowLineIdle.toPx()) * held
    val cy = size.height / 2f
    val top = cy - line / 2f
    val radius = CornerRadius(line / 2f)
    drawRoundRect(inactive.copy(alpha = inactive.alpha * 0.65f), Offset(0f, top), Size(width, line), radius)
    val played = (width * position).coerceAtLeast(line).coerceAtMost(width)
    if (position <= 0f || played <= 0f) return

    val bright = lerp(accents.accent, accents.light, 0.4f)
    val fill = lerp(bright, Color.White, 0.25f * held)
    val head = lerp(fill, Color.White, motion.lit.value)
    val whiteFrom = (1f - GlowWhiteRun.toPx() / played).coerceIn(0f, 1f)
    val whiteTo = (1f - GlowWhiteHead.toPx() / played).coerceIn(whiteFrom, 1f)
    drawRoundRect(
        brush = Brush.horizontalGradient(
            colorStops = arrayOf(0f to fill, whiteFrom to fill, whiteTo to head, 1f to head),
            startX = 0f,
            endX = played,
        ),
        topLeft = Offset(0f, top),
        size = Size(played, line),
        cornerRadius = radius,
    )

    // A run of teardrops that all reach the playhead, each centred further back, taller
    // and fainter than the last: brightest by the playhead, fanning out behind, no edge.
    val strength = motion.flare.value * motion.breath.value
    if (strength <= 0f) return
    val step = GlowStep.toPx()
    val reach = (played / (step * GlowLayers)).coerceIn(0f, 1f)
    val light = lerp(fill, Color.White, 0.75f)
    val base = GlowSpreadBase.toPx()
    for (i in 0 until GlowLayers) {
        val back = step * (i + 1) * reach
        teardrop(
            center = Offset(played - back, cy),
            front = back,
            back = back * 0.5f,
            ry = base + back * GlowSpreadSlope,
            color = light,
            alpha = GlowLayerAlpha * (1f - 0.65f * i / (GlowLayers - 1)) * strength,
        )
    }
}

/** A soft light lying on its side: a radial falloff stretched [front] px ahead and [back] px behind. */
private fun DrawScope.teardrop(center: Offset, front: Float, back: Float, ry: Float, color: Color, alpha: Float) {
    if (ry <= 0f) return
    val brush = Brush.radialGradient(
        colorStops = arrayOf(
            0f to color.copy(alpha = alpha),
            0.3f to color.copy(alpha = alpha * 0.55f),
            0.65f to color.copy(alpha = alpha * 0.15f),
            1f to color.copy(alpha = 0f),
        ),
        center = center,
        radius = ry,
    )
    if (back > 0f) {
        clipRect(left = center.x - back - 1f, top = center.y - ry, right = center.x, bottom = center.y + ry) {
            scale(scaleX = back / ry, scaleY = 1f, pivot = center) { drawCircle(brush = brush, radius = ry, center = center) }
        }
    }
    if (front > 0f) {
        clipRect(left = center.x, top = center.y - ry, right = center.x + front + 1f, bottom = center.y + ry) {
            scale(scaleX = front / ry, scaleY = 1f, pivot = center) { drawCircle(brush = brush, radius = ry, center = center) }
        }
    }
}

/**
 * The Wave style: a hairline with three soft ranges of humps rising out of its played
 * part, in a light tint, a dark shade and the accent itself, blended additively so their
 * overlaps brighten, tapering to nothing at both ends; the playhead is a white bead.
 */
private fun DrawScope.drawWaveStyle(
    width: Float,
    position: Float,
    touch: Float,
    motion: StyleMotion,
    accents: ProgressAccents,
    active: Color,
    inactive: Color,
    path: Path,
) {
    val line = WaveLine.toPx() + (WaveLineActive.toPx() - WaveLine.toPx()) * touch
    val cy = size.height / 2f
    val top = cy - line / 2f
    val radius = CornerRadius(line / 2f)
    drawRoundRect(inactive, Offset(0f, top), Size(width, line), radius)
    val played = width * position
    if (played > 0f) {
        clipRect(right = played) { drawRoundRect(active, Offset(0f, top), Size(width, line), radius) }
    }
    val swell = motion.waveSwell.value
    if (played > 1f && swell > 0f) {
        val maxHeight = WaveHeight.toPx() * swell
        val taper = min(played * 0.4f, WaveTaper.toPx())
        val step = 2.dp.toPx()
        val time = motion.time.floatValue
        WaveLayers.forEach { layer ->
            val wavelength = layer.wavelength.toPx()
            val travel = layer.speed.toPx() * time
            path.reset()
            path.moveTo(0f, top)
            var x = 0f
            while (true) {
                val envelope = smoothstep(x / taper) * smoothstep((played - x) / taper)
                val u = (x - travel) / wavelength + layer.phase
                val c = 0.5f + 0.5f * cos(2f * PI.toFloat() * u)
                // A slower beat across the humps, so neighbours don't stand at the same height.
                val beat = 1f - layer.unevenness * (0.5f + 0.5f * sin(2f * PI.toFloat() * (u * 0.37f + layer.phase)))
                path.lineTo(x, top - maxHeight * layer.height * envelope * c * c * beat)
                if (x >= played) break
                x = min(played, x + step)
            }
            path.lineTo(played, top)
            path.close()
            val color = when (layer.tone) {
                0 -> accents.light
                1 -> accents.dark
                else -> accents.accent
            }
            drawPath(path, color.copy(alpha = layer.alpha), blendMode = BlendMode.Plus)
        }
    }
    // The playhead: a white bead with an accent core and a soft halo.
    val bead = 1f + 0.4f * touch
    val center = Offset(played.coerceIn(0f, width), cy)
    val halo = WaveThumbHalo.toPx()
    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.45f), Color.Transparent), center, halo), halo, center)
    drawCircle(Color.White, WaveThumbRadius.toPx() * bead, center)
    drawCircle(accents.accent, WaveThumbCore.toPx() * bead, center)
}

private class WaveLayer(
    val tone: Int,
    val height: Float,
    val wavelength: Dp,
    val speed: Dp,
    val phase: Float,
    val alpha: Float,
    val unevenness: Float,
)

// Back to front: light, dark, accent. Different wavelengths and speeds, so the humps
// slide past one another and the overlaps keep changing.
private val WaveLayers = listOf(
    WaveLayer(0, height = 0.55f, wavelength = 92.dp, speed = 30.dp, phase = 0.35f, alpha = 0.85f, unevenness = 0.35f),
    WaveLayer(1, height = 0.72f, wavelength = 128.dp, speed = 40.dp, phase = 0.5f, alpha = 0.72f, unevenness = 0.3f),
    WaveLayer(2, height = 1f, wavelength = 150.dp, speed = 48.dp, phase = 0f, alpha = 0.72f, unevenness = 0.25f),
)

private fun smoothstep(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

private val GlowLineIdle = 4.5.dp
private val GlowLineActive = 7.dp
private val GlowWhiteRun = 52.dp
private val GlowWhiteHead = 12.dp
private const val GlowLayers = 7
private val GlowStep = 10.dp
private val GlowSpreadBase = 2.dp
private const val GlowSpreadSlope = 0.22f
private const val GlowLayerAlpha = 0.34f
private const val GlowBreathLow = 0.82f
private const val GlowBreathMs = 1600
private val WaveLine = 3.dp
private val WaveLineActive = 6.dp
private val WaveHeight = 9.dp
private val WaveTaper = 72.dp
private const val WaveSwellMs = 600
private val WaveThumbRadius = 5.dp
private val WaveThumbCore = 3.dp
private val WaveThumbHalo = 10.dp

private val ScrubberTrackHeight = 5.dp
private val ScrubberActiveTrackHeight = 11.dp
private val ScrubberTouchHeight = 32.dp
