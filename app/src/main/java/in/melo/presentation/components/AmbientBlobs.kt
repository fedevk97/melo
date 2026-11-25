/*
 * Licensed under the GNU General Public License v3.0; see LICENSE.
 */
package `in`.melo.presentation.components

import androidx.compose.ui.composed
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * [target] as the blobs draw it: its brightness held between a floor and a ceiling, so a pale
 * cover never washes the page out under its text and a dark one still leaves some colour.
 */
private fun ambientTone(target: Color): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(target.toArgb(), hsv)
    hsv[2] = hsv[2].coerceIn(AMBIENT_MIN_VALUE, AMBIENT_MAX_VALUE)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

class AmbientBlobs internal constructor(
    internal val primary: () -> Color,
    internal val secondary: () -> Color,
    internal val phase: State<Float>,
    internal val strength: Float,
)

/**
 * Three soft colour blobs that drift slowly behind a page: [primary], a dimmer [primary] and
 * [secondary], crossfading when the colours change. Plain radial gradients, no blur pass. With
 * [animate] off (reduced motion) they hold still. [dark] picks how strongly they tint the page.
 */
@Composable
fun rememberAmbientBlobs(primary: Color, secondary: Color, animate: Boolean, dark: Boolean): AmbientBlobs {
    val first by animateColorAsState(ambientTone(primary), tween(AMBIENT_COLOR_FADE_MS), label = "ambientFirst")
    val second by animateColorAsState(ambientTone(secondary), tween(AMBIENT_COLOR_FADE_MS), label = "ambientSecond")
    // Stepped a few times a second, not every frame: the blobs are slow and soft enough that the
    // steps don't show, and each step redraws whatever glass samples the page.
    val phase = remember { mutableFloatStateOf(0.15f) }
    if (animate) {
        LaunchedEffect(Unit) {
            val start = withFrameNanos { it } - (phase.floatValue * AMBIENT_CYCLE_MS * 1_000_000L).toLong()
            while (true) {
                delay(AMBIENT_STEP_MS)
                val now = withFrameNanos { it }
                phase.floatValue = ((now - start) / 1_000_000L % AMBIENT_CYCLE_MS) / AMBIENT_CYCLE_MS.toFloat()
            }
        }
    }
    return AmbientBlobs({ first }, { second }, phase, if (dark) AMBIENT_ALPHA_DARK else AMBIENT_ALPHA_LIGHT)
}

/**
 * Whether the colour background is behind the pages, so pages leave theirs see-through and
 * cards take [pageCardColor]'s veil. Off again inside sheets, which are solid.
 */
val LocalAmbientBackground = compositionLocalOf { false }

/**
 * The accent chrome takes while the colour background is on: the blobs' colour, made
 * readable on the page. Null while it is off, so chrome keeps the theme's colour. Without
 * it a page could sit on a red wash with a white tab, two colours unrelated on one screen.
 */
val LocalAmbientAccent = compositionLocalOf<Color?> { null }

/**
 * [primary] as the accent for [LocalAmbientAccent], crossfading with the blobs themselves: the
 * colour background's colour (made readable with [`in`.melo.ui.theme.accentOf]) while it is [on]
 * in a colour of its own; null otherwise.
 */
@Composable
fun rememberAmbientAccent(primary: Color, on: Boolean, dark: Boolean): Color? {
    val accent by animateColorAsState(
        `in`.melo.ui.theme.accentOf(ambientTone(primary), dark),
        tween(AMBIENT_COLOR_FADE_MS),
        label = "ambientAccent",
    )
    return if (on) accent else null
}

/**
 * The accent for chrome that sits on the page (the mini player's progress, the selected tab):
 * the colour background's while that is on, else the playing song's, else [fallback].
 */
@Composable
fun pageAccent(fallback: Color = androidx.compose.material3.MaterialTheme.colorScheme.primary): Color =
    LocalAmbientAccent.current ?: `in`.melo.ui.theme.LocalNowPlayingTint.current ?: fallback

/** A page's own background: see-through over the colour background, the theme's otherwise. See [MeloPageScaffold]. */
@Composable
fun pageBackground(): Color =
    if (LocalAmbientBackground.current) Color.Transparent else androidx.compose.material3.MaterialTheme.colorScheme.background

/**
 * The fill of a card or grouped list lying on a page. Over the colour background it is a veil
 * (black in dark, white in light, a third opaque), so the wash shows through every card the
 * same way whatever the surface style; with the colour background off it is [fallback].
 * Sheets, dialogs and menus keep their own solid fills.
 */
@Composable
fun pageCardColor(fallback: Color): Color =
    if (!LocalAmbientBackground.current) fallback
    else (if (`in`.melo.ui.theme.LocalMeloDarkTheme.current) Color.Black else Color.White).copy(alpha = CARD_VEIL_ALPHA)

private const val CARD_VEIL_ALPHA = 0.35f

/** A second colour for one colour: the same hue turned a little and darkened. */
fun ambientCompanion(color: Color): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    hsv[0] = (hsv[0] + 28f) % 360f
    hsv[2] = hsv[2] * 0.8f
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/** Draws [blobs] behind this; a null [blobs] draws nothing. */
fun Modifier.ambientBlobs(blobs: AmbientBlobs?): Modifier =
    if (blobs == null) this else drawBehind {
        // Each blob runs its own Lissajous path at a different whole-number speed, so they keep
        // crossing while the loop still joins up.
        val t = blobs.phase.value * 2f * PI.toFloat()
        val w = size.width
        val h = size.height
        val radius = max(w, h) * AMBIENT_RADIUS_FRACTION
        val main = blobs.primary()
        val a = blobs.strength
        listOf(
            Triple(Offset(w * (0.5f + 0.38f * sin(t)), h * (0.3f + 0.24f * sin(2f * t + 0.6f))), main, a),
            Triple(
                Offset(w * (0.5f + 0.38f * cos(2f * t + 1.3f)), h * (0.6f + 0.3f * sin(t + 2.2f))),
                main.copy(red = main.red * 0.7f, green = main.green * 0.7f, blue = main.blue * 0.7f),
                a * 0.9f,
            ),
            Triple(Offset(w * (0.5f + 0.4f * sin(3f * t + 2.4f)), h * (0.5f + 0.34f * cos(t + 0.9f))), blobs.secondary(), a * 0.95f),
        ).forEach { (centre, colour, strength) ->
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(colour.copy(alpha = strength), colour.copy(alpha = strength * 0.45f), Color.Transparent),
                    center = centre,
                    radius = radius,
                ),
                radius = radius,
                center = centre,
            )
        }
    }

private const val AMBIENT_MAX_VALUE = 0.85f
private const val AMBIENT_MIN_VALUE = 0.6f
private const val AMBIENT_ALPHA_DARK = 0.30f
private const val AMBIENT_ALPHA_LIGHT = 0.30f
private const val AMBIENT_RADIUS_FRACTION = 0.48f
private const val AMBIENT_CYCLE_MS = 36_000
private const val AMBIENT_COLOR_FADE_MS = 900
private const val AMBIENT_STEP_MS = 80L

/**
 * How far a page has scrolled, read from the scrolls inside it: [offsetPx] is that distance
 * (zero or less, as a header's translation), [fraction] goes from 0 at the top to 1 a little
 * way down. A page's header rides up with it by [offsetPx] instead of sitting
 * over the content as a filled bar.
 */
class ScrolledUnder internal constructor(private val fullAfterPx: Float) {
    private var offset by mutableFloatStateOf(0f)
    val offsetPx: Float get() = offset
    val fraction: Float get() = (-offset / fullAfterPx).coerceIn(0f, 1f)

    /** Back to the top, for when the page swaps its content for other content. */
    fun reset() { offset = 0f }
    val connection = object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: androidx.compose.ui.input.nestedscroll.NestedScrollSource,
        ): Offset {
            offset = (offset + consumed.y).coerceAtMost(0f)
            return Offset.Zero
        }
    }
}

@Composable
fun rememberScrolledUnder(): ScrolledUnder {
    val px = with(androidx.compose.ui.platform.LocalDensity.current) { 24.dp.toPx() }
    return remember(px) { ScrolledUnder(px) }
}

/**
 * The soft fade along the top of a page once it scrolls: the page colour under
 * the status bar easing to nothing a little below it, so content leaving the top doesn't run
 * hard into the clock. It is a fade, not a bar, so the colour background still shows through.
 */
@Composable
fun StatusBarFade(shown: () -> Float) {
    val color = androidx.compose.material3.MaterialTheme.colorScheme.background
    val inset = androidx.compose.foundation.layout.WindowInsets.statusBars
        .asPaddingValues().calculateTopPadding()
    val brush = remember(color) {
        Brush.verticalGradient(
            colorStops = Array(8) { i ->
                val t = i / 7f
                t to color.copy(alpha = 0.85f * (1f - t) * (1f - t))
            },
        )
    }
    androidx.compose.foundation.layout.Box(
        Modifier
            .fillMaxWidth()
            .height(inset + 28.dp)
            .drawBehind { val a = shown(); if (a > 0f) drawRect(brush, alpha = a) },
    )
}

/**
 * The colour background's light caught along a surface's edge, as glass whose rim
 * saturates what lies under it and so takes the wash's colour: a rim in the song's colour,
 * brighter along the top, over a faint wash of it in the fill. [strength] fades it (the mini
 * player as it opens). Nothing while the colour background is off.
 */
fun Modifier.ambientRim(shape: androidx.compose.ui.graphics.Shape, strength: () -> Float = { 1f }): Modifier =
    composed {
        val accent = LocalAmbientAccent.current ?: return@composed Modifier
        val dark = `in`.melo.ui.theme.LocalMeloDarkTheme.current
        val top = androidx.compose.ui.graphics.lerp(accent, Color.White, if (dark) 0.4f else 0.2f)
            .copy(alpha = if (dark) 0.85f else 0.8f)
        val bottom = accent.copy(alpha = if (dark) 0.5f else 0.45f)
        val fill = accent.copy(alpha = if (dark) 0.10f else 0.08f)
        Modifier.drawWithContent {
            val shown = strength().coerceIn(0f, 1f)
            val outline = shape.createOutline(size, layoutDirection, this)
            if (shown > 0f) drawOutline(outline, fill, alpha = shown)
            drawContent()
            if (shown > 0f) drawOutline(
                outline, Brush.verticalGradient(listOf(top, bottom)), alpha = shown,
                style = androidx.compose.ui.graphics.drawscope.Stroke(RIM_WIDTH.toPx()),
            )
        }
    }

private val RIM_WIDTH = 1.25.dp
