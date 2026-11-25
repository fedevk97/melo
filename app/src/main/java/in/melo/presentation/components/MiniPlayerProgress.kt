/*
 * Licensed under GPL-3.0; see LICENSE.
 */
package `in`.melo.presentation.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How far through the song is, drawn along the bottom of the mini player in the song's
 * colour, brightening towards the playhead.
 *
 * Plain ([glow] false): a thin line inside the bar. Glow: soft light rising inside the bar
 * from its bottom edge with a brighter line along the edge, a little of it spilling below
 * along the flat of the bottom, all of it tapering away over the last stretch before the
 * playhead. [shape] is the bar's, so the light follows its corners; [corner] is how far its
 * rounded ends reach in, where nothing spills.
 *
 * The playhead is read while drawing, so playback redraws the bar and recomposes nothing.
 */
fun Modifier.miniPlayerProgress(
    positionMs: () -> Long,
    durationMs: () -> Long,
    color: Color,
    glow: Boolean = true,
    shape: Shape = RectangleShape,
    corner: (Size) -> Float = { 0f },
): Modifier = drawWithContent {
    drawContent()
    val duration = durationMs()
    if (duration <= 0L) return@drawWithContent
    val fraction = (positionMs().toFloat() / duration).coerceIn(0f, 1f)
    if (fraction <= 0f) return@drawWithContent
    val end = size.width * fraction
    val light = lerp(color, Color.White, 0.45f)
    val line = Brush.horizontalGradient(listOf(color, light), startX = 0f, endX = end.coerceAtLeast(1f))
    val outline = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithContent)) }
    if (!glow) {
        val h = PLAIN_LINE.toPx()
        clipPath(outline) {
            drawRect(line, topLeft = Offset(0f, size.height - h), size = Size(end, h))
        }
        return@drawWithContent
    }
    val rise = GLOW_RISE.toPx()
    val spill = GLOW_SPILL.toPx()
    val edge = GLASS_LINE.toPx()
    val inset = corner(size)
    // In a layer of its own, faded out towards the playhead as a whole, so the glow and the
    // line taper off together rather than stopping at a hard edge.
    drawContext.canvas.saveLayer(Rect(0f, 0f, size.width, size.height + spill), Paint())
    clipPath(outline) {
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                1f to color.copy(alpha = 0.42f),
                startY = size.height - rise,
                endY = size.height,
            ),
            topLeft = Offset(0f, size.height - rise),
            size = Size(end, rise),
        )
        drawRect(line, topLeft = Offset(0f, size.height - edge), size = Size(end, edge))
    }
    val spillEnd = end.coerceAtMost(size.width - inset)
    if (spillEnd > inset) {
        drawRect(
            brush = Brush.verticalGradient(
                listOf(color.copy(alpha = 0.32f), Color.Transparent),
                startY = size.height,
                endY = size.height + spill,
            ),
            topLeft = Offset(inset, size.height),
            size = Size(spillEnd - inset, spill),
        )
    }
    val taper = minOf(GLOW_TAPER.toPx(), end * 0.5f)
    // The mask covers the whole layer, not just up to the playhead, so the antialiased column
    // at the playhead never flickers as a faint bright line.
    drawRect(
        brush = Brush.horizontalGradient(
            0f to Color.Black,
            ((end - taper) / end).coerceIn(0f, 1f) to Color.Black,
            1f to Color.Transparent,
            startX = 0f,
            endX = end,
        ),
        topLeft = Offset.Zero,
        size = Size(size.width, size.height + spill),
        blendMode = BlendMode.DstIn,
    )
    drawContext.canvas.restore()
}

/** [color] made bright enough to read as light on the bar. */
fun progressTint(color: Color): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    hsv[1] = hsv[1].coerceAtLeast(0.45f)
    hsv[2] = hsv[2].coerceAtLeast(0.8f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

private val PLAIN_LINE: Dp = 2.5.dp
private val GLASS_LINE: Dp = 2.dp
private val GLOW_RISE: Dp = 16.dp
private val GLOW_SPILL: Dp = 6.dp
private val GLOW_TAPER: Dp = 36.dp
