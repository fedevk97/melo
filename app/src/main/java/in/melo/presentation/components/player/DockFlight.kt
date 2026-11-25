package `in`.melo.presentation.components.player

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import `in`.melo.presentation.components.MiniArtSize
import `in`.melo.presentation.components.MiniPlayerHeight
import `in`.melo.presentation.components.MiniRowPadding

/** How far in from the sheet's left edge the mini player starts this frame, in px. */
internal val LocalMiniCoverStartPx = staticCompositionLocalOf<() -> Float> { { 0f } }

/** The mini player's cover corner. */
private val MiniCoverCorner = 8.dp

/**
 * The full player is faded out by the time the sheet is this open; the cover lands on the
 * mini player's at that point, so the hand-over is one picture on one spot.
 */
private const val DOCK_LANDING_FRACTION = 0.06f

/** How far the cover is from the full player toward the mini player's cover, 0 to 1. */
internal fun dockProgress(expansion: Float): Float =
    ((1f - expansion) / (1f - DOCK_LANDING_FRACTION)).coerceIn(0f, 1f)

/**
 * Flies the cover between its place on the full player and the mini player's cover as the
 * sheet folds: it narrows to the square at its centre, shrinks and rounds its corners, landing
 * on the mini cover as the sheet becomes the bar, and grows back out of it on the way up.
 * Read at draw, so the flight is a redraw per frame.
 */
internal fun Modifier.dockFlight(
    expansion: () -> Float,
    miniStartPx: () -> Float,
    restingCorner: Dp,
): Modifier = graphicsLayer {
    val c = dockProgress(expansion())
    if (c <= 0f) {
        clip = false
        return@graphicsLayer
    }
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return@graphicsLayer
    val side = minOf(w, h)
    val whole = Rect(0f, 0f, w, h)
    val square = Rect((w - side) / 2f, (h - side) / 2f, (w + side) / 2f, (h + side) / 2f)
    val kept = lerp(whole, square, c)
    val miniSide = MiniArtSize.toPx()
    val miniLeft = miniStartPx() + MiniRowPadding.toPx()
    val miniTop = (MiniPlayerHeight.toPx() - miniSide) / 2f
    val dest = lerp(whole, Rect(miniLeft, miniTop, miniLeft + miniSide, miniTop + miniSide), c)
    val scale = dest.width / kept.width
    transformOrigin = TransformOrigin(0f, 0f)
    scaleX = scale
    scaleY = scale
    translationX = dest.left - kept.left * scale
    translationY = dest.top - kept.top * scale
    clip = true
    shape = KeptShape(kept, lerp(restingCorner.toPx(), MiniCoverCorner.toPx(), c) / scale)
}

/** The part of the cover still showing, in the cover's own unscaled pixels. */
private class KeptShape(private val rect: Rect, private val corner: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(Path().apply { addRoundRect(RoundRect(rect, CornerRadius(corner))) })
}
