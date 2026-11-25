package `in`.melo.presentation.components.scoped

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.util.lerp

/**
 * Holds references needed to compute full-player visual properties lazily.
 *
 * **Key design**: [contentAlpha] and [translationY] are computed on-demand via getters
 * that read [Animatable.value]. When called inside `graphicsLayer { }`, these reads
 * happen during the draw phase and trigger **re-draw only** — not recomposition.
 * This eliminates per-frame recomposition of the parent composable during gestures.
 */
internal class FullPlayerVisualState(
    private val expansionFraction: Animatable<Float, AnimationVector1D>,
    private val initialOffsetY: Float
) {
    /**
     * Full-player fade-in: held for the first 6 % while the mini row
     * is still there, then in over the next 20 %, together with the panel's fill.
     */
    val contentAlpha: Float
        get() {
            val f = expansionFraction.value
            return ((f - 0.06f) / 0.2f).coerceIn(0f, 1f)
        }

    /** Slide-up entrance driven by [contentAlpha]. */
    val translationY: Float
        get() = lerp(initialOffsetY, 0f, contentAlpha)
}

/**
 * How far the full player is slid down this frame ([FullPlayerVisualState.translationY]), read at
 * draw. The player's backdrop moves back up by it, so only the controls and artwork slide in: a
 * backdrop sliding with them left a band of the sheet's own fill above it, a second top edge.
 */
internal val LocalFullPlayerSlide = staticCompositionLocalOf<() -> Float> { { 0f } }

@Composable
internal fun rememberFullPlayerVisualState(
    expansionFraction: Animatable<Float, AnimationVector1D>,
    initialOffsetY: Float
): FullPlayerVisualState {
    return remember(expansionFraction, initialOffsetY) {
        FullPlayerVisualState(expansionFraction, initialOffsetY)
    }
}
