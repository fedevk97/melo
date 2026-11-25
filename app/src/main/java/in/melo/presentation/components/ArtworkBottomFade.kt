package `in`.melo.presentation.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Share of a full-screen cover's height given over to its dissolve.
 *
 * Generous on purpose: the cover has no card edge to stop at, so a short fade
 * reads as a picture that was cut off rather than one that ran out.
 */
internal const val FULL_BLEED_FADE_FRACTION = 0.42f

/**
 * Dissolves the bottom [fraction] of the content into whatever is drawn behind
 * it, in a straight ramp: a curve flattened at its foot left the picture's last
 * stretch reading as a band. The mask erases what the layer drew, which only
 * works in a buffer of its own, hence the offscreen layer.
 *
 * [floor] is how much of the picture the foot keeps, 0 for a full dissolve; the
 * sleeve flying to a thumbnail raises it to 1 so it lands whole.
 */
internal fun Modifier.artworkBottomFade(
    fraction: Float = FULL_BLEED_FADE_FRACTION,
    floor: () -> Float = { 0f },
): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val keep = floor().coerceIn(0f, 1f)
            if (keep >= 1f) return@drawWithContent
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Black, Color.Black.copy(alpha = keep)),
                    startY = size.height * (1f - fraction),
                    endY = size.height,
                ),
                blendMode = BlendMode.DstIn,
            )
        }
