package `in`.melo.presentation.components.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.math.max
import kotlin.math.min

/** The artwork's own accent, under the player's fixed dark scheme; null when it has none. */
internal val LocalPlayerArtAccent = compositionLocalOf<Color?> { null }

/** How far toward the cover's hue the white ink goes: a cast, never a colour of its own. */
internal const val PLAYER_INK_TINT = 0.28f

/** Below this saturation an accent is a grey, and the ink stays white rather than taking a muddy cast. */
private const val INK_GREY_SATURATION = 0.08f

/** Each new cover's ink comes in over this long, so a skip recolours the player rather than flashing it. */
private const val INK_CHANGE_MS = 900

/**
 * White ink with a cast of [accent]'s hue, taken at a fixed lightness and at least moderate
 * saturation so a pale or murky accent still gives the same strength of cast.
 */
internal fun playerInkFor(accent: Color?): Color {
    if (accent == null) return Color.White
    val (h, s, _) = accent.toHsl()
    if (s < INK_GREY_SATURATION) return Color.White
    return lerp(Color.White, Color.hsl(h, s.coerceAtLeast(0.6f), 0.6f), PLAYER_INK_TINT)
}

/** [playerInkFor] the current cover, easing from one cover's ink to the next. */
@Composable
internal fun rememberPlayerInk(): Color {
    val accent = LocalPlayerArtAccent.current
    val target = remember(accent) { playerInkFor(accent) }
    val ink by animateColorAsState(target, tween(INK_CHANGE_MS, easing = FastOutSlowInEasing), label = "playerInk")
    return ink
}

private fun Color.toHsl(): Triple<Float, Float, Float> {
    val maxC = max(red, max(green, blue))
    val minC = min(red, min(green, blue))
    val l = (maxC + minC) / 2f
    val d = maxC - minC
    if (d == 0f) return Triple(0f, 0f, l)
    val s = d / (1f - kotlin.math.abs(2f * l - 1f))
    val h = when (maxC) {
        red -> 60f * (((green - blue) / d).mod(6f))
        green -> 60f * ((blue - red) / d + 2f)
        else -> 60f * ((red - green) / d + 4f)
    }
    return Triple(h.mod(360f), s.coerceIn(0f, 1f), l)
}
