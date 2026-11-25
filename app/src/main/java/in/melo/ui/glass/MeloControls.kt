/*
 * Melo's touch controls: the switch, the thin slider and the press dent.
 *
 * All three are made of the same glass. Licensed under GPL-3.0; see LICENSE.
 */
package `in`.melo.ui.glass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderState
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------------------------
// Shared glass film
// ---------------------------------------------------------------------------------------------

@Composable
@ReadOnlyComposable
internal fun controlsDark(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

/**
 * How dense the frosted controls are drawn: in the Glass style it follows the glass transparency,
 * measured from where it ships, so clearer glass gives clearer controls; 1 otherwise.
 */
@Composable
@ReadOnlyComposable
internal fun frostedDensity(): Float {
    val options = LocalMeloSurface.current
    if (options.style != MeloSurfaceStyle.GLASS) return 1f
    val reference = 1f - MeloSurfaceOptions.DEFAULT_TRANSPARENCY
    return ((1f - options.transparency) / reference).coerceIn(0.55f, 1.3f)
}

/** A faint film either side of the theme: an off switch, an empty track. */
internal fun frostedTrackTint(dark: Boolean, density: Float): Color =
    if (dark) Color.White.copy(alpha = (0.16f * density).coerceIn(0f, 1f))
    else Color.Black.copy(alpha = (0.09f * density).coerceIn(0f, 1f))

/** An on switch: the system green, see-through enough to stay glass. */
internal fun frostedOnTint(dark: Boolean, density: Float): Color =
    (if (dark) Color(0xFF30D158) else Color(0xFF34C759)).copy(alpha = (0.78f * density).coerceIn(0f, 1f))

internal fun frostedRim(dark: Boolean): Color =
    if (dark) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.55f)

/** The frosted film on any shape: a see-through [tint], a sheen across the top, a light rim. */
internal fun Modifier.frostedGlassFill(tint: Color, shape: Shape, dark: Boolean): Modifier =
    clip(shape).background(tint, shape).background(TRACK_SHEEN, shape).border(EDGE, frostedRim(dark), shape)

/** How milky a thumb is at rest. */
internal fun thumbAlpha(dark: Boolean): Float = if (dark) 0.84f else 0.9f

internal val TRACK_SHEEN = Brush.verticalGradient(
    0f to Color.White.copy(alpha = 0.20f),
    0.55f to Color.White.copy(alpha = 0.03f),
    1f to Color.Transparent,
)

/** A bead of glass: brighter at the top, a little shade at the foot. */
internal val THUMB_SHEEN = Brush.verticalGradient(
    0f to Color.White.copy(alpha = 0.55f),
    0.5f to Color.White.copy(alpha = 0.08f),
    1f to Color.Black.copy(alpha = 0.06f),
)

internal val THUMB_RIM = Color.White.copy(alpha = 0.7f)
internal val EDGE = 0.5.dp

// ---------------------------------------------------------------------------------------------
// Switch
// ---------------------------------------------------------------------------------------------

/**
 * A wide capsule with a pill of a thumb that stretches toward where it is going while pressed,
 * in frosted glass: plain drawing, no backdrop capture, so a page full of them scrolls freely.
 *
 * It takes Material3's `Switch` call shape, so a row adopts it by name. [thumbContent] and
 * [colors] are accepted and ignored: on is the system green whatever the accent.
 *
 * A null [onCheckedChange] makes it a read-out; the row around it owns the tap. It buzzes when
 * [checked] flips, so a row that toggles from its own click still gets the tick.
 */
@Composable
fun MeloSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") thumbContent: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    @Suppress("UNUSED_PARAMETER") colors: SwitchColors? = null,
    interactionSource: MutableInteractionSource? = null,
) {
    val dark = controlsDark()
    val interaction = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val density = frostedDensity()

    val position by animateFloatAsState(
        if (checked) 1f else 0f, spring(dampingRatio = 0.78f, stiffness = 700f), label = "switchThumb",
    )
    val stretch by animateFloatAsState(
        if (pressed) 1.18f else 1f, spring(dampingRatio = 0.7f, stiffness = 900f), label = "switchStretch",
    )
    val clarity by animateFloatAsState(
        if (pressed) 0.55f else 1f, spring(dampingRatio = 0.8f, stiffness = 900f), label = "switchClarity",
    )
    val track by animateColorAsState(
        if (checked) frostedOnTint(dark, density) else frostedTrackTint(dark, density), label = "switchTrack",
    )
    val rim = frostedRim(dark)
    val thumbFill = Color.White.copy(alpha = thumbAlpha(dark) * clarity)
    val ltr = LocalLayoutDirection.current == LayoutDirection.Ltr

    val haptics = LocalHapticFeedback.current
    // Skips the first run, so a switch scrolling into view stays quiet.
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(checked) {
        if (settled) haptics.performHapticFeedback(if (checked) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
        settled = true
    }

    Box(
        modifier = modifier
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .then(
                if (onCheckedChange != null) {
                    Modifier.minimumInteractiveComponentSize().toggleable(
                        value = checked,
                        enabled = enabled,
                        interactionSource = interaction,
                        indication = null,
                        role = Role.Switch,
                        onValueChange = onCheckedChange,
                    )
                } else Modifier,
            )
            .size(SwitchTrackWidth, SwitchTrackHeight)
            .drawBehind {
                val radius = CornerRadius(size.height / 2f)
                drawRoundRect(color = track, cornerRadius = radius)
                drawRoundRect(brush = TRACK_SHEEN, cornerRadius = radius)
            }
            .border(EDGE, rim, CircleShape),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .graphicsLayer {
                    val travel = (SwitchTrackWidth - SwitchThumbWidth - SwitchThumbPadding * 2).toPx()
                    val x = SwitchThumbPadding.toPx() + travel * position
                    translationX = if (ltr) x else -x
                    // Grows from the edge it is leaving, so the stretch reads as reaching across.
                    transformOrigin = TransformOrigin(if (checked == ltr) 1f else 0f, 0.5f)
                    scaleX = stretch
                }
                .size(SwitchThumbWidth, SwitchThumbHeight)
                .shadow(1.5.dp, CircleShape, clip = false)
                .drawBehind {
                    val radius = CornerRadius(size.height / 2f)
                    drawRoundRect(color = thumbFill, cornerRadius = radius)
                    drawRoundRect(brush = THUMB_SHEEN, cornerRadius = radius)
                }
                .border(EDGE, THUMB_RIM, CircleShape),
        )
    }
}

private val SwitchTrackWidth = 64.dp
private val SwitchTrackHeight = 28.dp
private val SwitchThumbWidth = 40.dp
private val SwitchThumbHeight = 24.dp
private val SwitchThumbPadding = 2.dp

// ---------------------------------------------------------------------------------------------
// Thin slider
// ---------------------------------------------------------------------------------------------

/**
 * A slider drawn thin: a 5dp frosted track filled with the accent up to the value, and a milky
 * glass capsule for a thumb that swells and clears while it is held. It is Material3's `Slider`
 * underneath, so steps, semantics and keyboard all behave as before; only the drawing changes.
 * Steps tick under the finger as the value crosses them; no tick marks are drawn.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeloSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    @Suppress("UNUSED_PARAMETER") colors: SliderColors? = null,
    /** The fill up to the value; the theme's accent unless given. */
    activeColor: Color = MaterialTheme.colorScheme.primary,
    interactionSource: MutableInteractionSource? = null,
) {
    val dark = controlsDark()
    val density = frostedDensity()
    val interaction = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val dragged by interaction.collectIsDraggedAsState()
    val held = pressed || dragged
    val swell by animateFloatAsState(
        if (held) 1.22f else 1f, spring(dampingRatio = 0.6f, stiffness = 800f), label = "sliderSwell",
    )
    val clarity by animateFloatAsState(
        if (held) 0.6f else 1f, spring(dampingRatio = 0.8f, stiffness = 900f), label = "sliderClarity",
    )
    val emptyTint = frostedTrackTint(dark, density)
    val rim = frostedRim(dark)
    val fill = if (enabled) activeColor else activeColor.copy(alpha = 0.38f)

    // A tick at each step the value crosses, as a detent would give.
    val haptics = LocalHapticFeedback.current
    val stepIndex = if (steps > 0) {
        val span = valueRange.endInclusive - valueRange.start
        if (span > 0f) (((value - valueRange.start) / span) * (steps + 1)).let { kotlin.math.round(it).toInt() } else 0
    } else -1
    var lastStep by remember { mutableStateOf(stepIndex) }
    LaunchedEffect(stepIndex) {
        if (stepIndex >= 0 && stepIndex != lastStep && held) {
            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
        lastStep = stepIndex
    }

    // The custom thumb and track only come with the state-holding overload.
    val state = remember(valueRange, steps) { SliderState(value, steps, valueRange) }
    state.value = value.coerceIn(valueRange.start, valueRange.endInclusive)
    val span = valueRange.endInclusive - valueRange.start
    val fraction = if (span > 0f) ((state.value - valueRange.start) / span).coerceIn(0f, 1f) else 0f
    Slider(
        state = state,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        onValueChangeFinished = onValueChangeFinished,
        interactionSource = interaction,
        thumb = { _: SliderState ->
            Box(
                Modifier
                    .size(SliderThumbWidth, SliderThumbHeight)
                    .graphicsLayer {
                        scaleX = swell
                        scaleY = swell
                        alpha = if (enabled) 1f else 0.6f
                    }
                    .shadow(2.dp, CircleShape, clip = false)
                    .drawBehind {
                        val radius = CornerRadius(size.height / 2f)
                        drawRoundRect(Color.White.copy(alpha = thumbAlpha(dark) * clarity), cornerRadius = radius)
                        drawRoundRect(brush = THUMB_SHEEN, cornerRadius = radius)
                    }
                    .border(EDGE, THUMB_RIM, CircleShape),
            )
        },
        track = { _: SliderState -> ThinTrack({ fraction }, fill, emptyTint, rim) },
    )
}

@Composable
private fun ThinTrack(fraction: () -> Float, fill: Color, empty: Color, rim: Color) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(SliderTrackHeight)
            .drawBehind {
                val radius = CornerRadius(size.height / 2f)
                drawRoundRect(empty, cornerRadius = radius)
                val width = size.width * fraction()
                if (width > 0f) {
                    val rtl = layoutDirection == LayoutDirection.Rtl
                    drawRoundRect(
                        fill,
                        topLeft = Offset(if (rtl) size.width - width else 0f, 0f),
                        size = Size(width, size.height),
                        cornerRadius = radius,
                    )
                }
                drawRoundRect(brush = TRACK_SHEEN, cornerRadius = radius)
            }
            .border(EDGE, rim, CircleShape),
    )
}

private val SliderTrackHeight = 5.dp
private val SliderThumbWidth = 34.dp
private val SliderThumbHeight = 22.dp

// ---------------------------------------------------------------------------------------------
// Press dent
// ---------------------------------------------------------------------------------------------

/**
 * A button that gives under the finger: at rest it is lit like a dome, light on top and shaded
 * underneath; pressed, it sinks a little and the light turns over, so it reads as a dish pushed
 * in. A tap too quick to see still sinks all the way before it springs back.
 *
 * Hand [interactions] to the button's clickable, put [pressSink] outside its surface and
 * [pressShade] inside its clip, under the glyph.
 */
@Stable
class PressDent internal constructor(val interactions: MutableInteractionSource) {
    internal val depth = Animatable(0f)
}

@Composable
fun rememberPressDent(): PressDent {
    val dent = remember { PressDent(MutableInteractionSource()) }
    LaunchedEffect(dent) {
        var sinking: Job? = null
        dent.interactions.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> sinking = launch { dent.depth.animateTo(1f, tween(PRESS_IN_MS)) }
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    val inward = sinking
                    launch {
                        inward?.join()
                        dent.depth.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow))
                    }
                }
            }
        }
    }
    return dent
}

/** The sink: outside the button's surface, so the whole button shrinks. */
fun Modifier.pressSink(dent: PressDent): Modifier = graphicsLayer {
    val s = 1f - PRESS_SCALE * dent.depth.value
    scaleX = s
    scaleY = s
}

/** The light turning over: inside the button's clip, over its surface and under its glyph. */
fun Modifier.pressShade(dent: PressDent): Modifier = drawBehind {
    val p = dent.depth.value.coerceIn(0f, 1f)
    val top = lerp(Color.White.copy(alpha = 0.10f), Color.Black.copy(alpha = 0.26f), p)
    val foot = lerp(Color.Black.copy(alpha = 0.10f), Color.White.copy(alpha = 0.14f), p)
    drawRect(Brush.verticalGradient(listOf(top, Color.Transparent, foot)))
}

private const val PRESS_IN_MS = 90
private const val PRESS_SCALE = 0.06f
