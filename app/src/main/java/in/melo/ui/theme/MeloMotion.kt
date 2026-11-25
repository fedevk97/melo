package `in`.melo.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import `in`.melo.presentation.utils.LocalReducedMotion

/**
 * Every timing in the app, named once.
 *
 * Fades and colour changes use the three durations; anything a finger moves, or that
 * lands somewhere, uses a spring. Keeping to these few is what makes the whole app feel
 * like one thing moving. The composable getters return a cut instead of an animation
 * when Android's "Remove animations" is on.
 */
object MeloMotion {
    /** A control reacting to a touch: press states, toggles, icon swaps. */
    const val Fast = 150

    /** Something appearing or changing in place: fades, colour, text swaps. */
    const val Standard = 220

    /** Something large arriving: a page's palette, a sheet's content, the player backdrop. */
    const val Emphasised = 320

    /** iOS's default curve: quick out of the blocks, long gentle landing. */
    val Easing: Easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

    /** A pressed control shrinking and coming back: firm, no wobble. */
    const val PressDamping = 0.8f
    const val PressStiffness = 900f

    /** Something moving into place (a pill, a page, a sheet): settles without bouncing. */
    const val MoveDamping = Spring.DampingRatioNoBouncy
    const val MoveStiffness = 500f

    /** A playful landing with a small overshoot (selection pill, like button). */
    const val BounceDamping = 0.6f
    const val BounceStiffness = 420f

    fun <T> fastSpec(): FiniteAnimationSpec<T> = tween(Fast, easing = Easing)
    fun <T> standardSpec(): FiniteAnimationSpec<T> = tween(Standard, easing = Easing)
    fun <T> emphasisedSpec(): FiniteAnimationSpec<T> = tween(Emphasised, easing = Easing)
    fun <T> pressSpec(): FiniteAnimationSpec<T> = spring(PressDamping, PressStiffness)
    fun <T> moveSpec(): FiniteAnimationSpec<T> = spring(MoveDamping, MoveStiffness)
    fun <T> bounceSpec(): FiniteAnimationSpec<T> = spring(BounceDamping, BounceStiffness)

    /** [spec], or a cut when the system asks for no animation. */
    @Composable
    @ReadOnlyComposable
    fun <T> orCut(spec: FiniteAnimationSpec<T>): FiniteAnimationSpec<T> =
        if (LocalReducedMotion.current) snap() else spec

    @Composable @ReadOnlyComposable fun <T> fast(): FiniteAnimationSpec<T> = orCut(fastSpec())
    @Composable @ReadOnlyComposable fun <T> standard(): FiniteAnimationSpec<T> = orCut(standardSpec())
    @Composable @ReadOnlyComposable fun <T> emphasised(): FiniteAnimationSpec<T> = orCut(emphasisedSpec())
    @Composable @ReadOnlyComposable fun <T> press(): FiniteAnimationSpec<T> = orCut(pressSpec())
    @Composable @ReadOnlyComposable fun <T> move(): FiniteAnimationSpec<T> = orCut(moveSpec())
    @Composable @ReadOnlyComposable fun <T> bounce(): FiniteAnimationSpec<T> = orCut(bounceSpec())
}
