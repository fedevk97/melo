// Modified for Melo on 2026-11-16. Licensed under GPL-3.0; see LICENSE.
package `in`.melo.presentation.screens

import androidx.compose.animation.core.DecayAnimation
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.MotionDurationScale
import kotlin.coroutines.coroutineContext
import kotlin.math.abs

private val SPEED_DIAL_PAGE_KEYS: Set<Any> = setOf("speed_dial")
private const val ARTIST_DIAL_PAGE_KEY = "artist_dial"

/**
 * Home's fling over its dial pages — the speed dial, then the artists' dial
 * when there is one: let go anywhere on one of them and the list settles on
 * that page or the next thing below it, like turning a page. Anywhere else it
 * glides on with [decay], the list's ordinary fling, until a dial page comes
 * on screen: then it settles on the dial or on what follows it, whichever the
 * glide would have left more of in view, so it never stops half-way.
 *
 * [restOffsetPx] is where the dial page with the given key sits, measured from
 * the content's start: the speed dial where it is with the list at its top
 * (under the mood chips), the artists' dial just under the title once the
 * chips have scrolled away. [nextOffsetPx] is where anything after the dials
 * comes to rest, clear of the top bar.
 * [artistPageFollows] says whether the speed dial has the artists' dial after it.
 *
 * Both the glide and the settle go by each frame's own time, see [runEven].
 */
internal class DialPageFling(
    private val listState: LazyListState,
    private val decay: DecayAnimationSpec<Float>,
    private val restOffsetPx: (key: Any) -> Int,
    private val nextOffsetPx: () -> Int,
    private val artistPageFollows: () -> Boolean = { false },
) : FlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        val page = straddlingPage()
        if (page == null) return glide(initialVelocity)
        val (toTop, toNext) = page
        val forward = when {
            initialVelocity > PAGE_FLING_VELOCITY -> true
            initialVelocity < -PAGE_FLING_VELOCITY -> false
            // A slow release: whichever it is past a third of the way to.
            else -> -toTop > (toNext - toTop) / 3f
        }
        settle(if (forward) toNext else toTop, initialVelocity)
        return 0f
    }

    private fun isDial(key: Any) = key in SPEED_DIAL_PAGE_KEYS || key == ARTIST_DIAL_PAGE_KEY

    /**
     * The dial page that holds the top of the screen, as the scrolls that bring
     * it back to rest and that bring what follows it up to its own: its
     * resting place is at or above where it is now, and some of it still shows
     * below where the following item would rest. Null when no dial page does.
     */
    private fun straddlingPage(): Pair<Float, Float>? {
        val next = nextOffsetPx()
        // Where whatever follows a dial page comes to rest.
        fun followingRest(key: Any) =
            if (key in SPEED_DIAL_PAGE_KEYS && artistPageFollows()) restOffsetPx(ARTIST_DIAL_PAGE_KEY) else next
        val page = listState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
            isDial(item.key) && item.offset <= restOffsetPx(item.key) && item.offset + item.size > followingRest(item.key)
        } ?: return null
        return (page.offset - restOffsetPx(page.key)).toFloat() to (page.offset + page.size + listState.layoutInfo.mainAxisItemSpacing - followingRest(page.key)).toFloat()
    }

    /**
     * The ordinary glide, cut short once a dial page comes on screen: from there
     * it settles on whichever of the dial and what follows it the glide would
     * have left more of in view.
     */
    private suspend fun ScrollScope.glide(initialVelocity: Float): Float {
        if (abs(initialVelocity) <= 1f) return finishOnPage(0f, initialVelocity)
        var remaining = initialVelocity
        var caught = false
        var last = 0f
        DecayAnimation(decay, Float.VectorConverter, 0f, initialVelocity).runEven { value, velocity ->
            val delta = value - last
            val consumed = scrollBy(delta)
            last = value
            remaining = velocity
            when {
                straddlingPage() != null -> { caught = true; false }
                abs(delta - consumed) > 0.5f -> { remaining = 0f; false }
                else -> true
            }
        }
        return if (caught) finishOnPage(remaining, remaining) else remaining
    }

    /** Settles on the dial page's side the glide at [velocity] would have shown more of. */
    private suspend fun ScrollScope.finishOnPage(velocity: Float, handOver: Float): Float {
        val (toTop, toNext) = straddlingPage() ?: return velocity
        val lands = decay.calculateTargetValue(0f, velocity)
        settle(if (lands > (toTop + toNext) / 2f) toNext else toTop, handOver)
        return 0f
    }

    private suspend fun ScrollScope.settle(target: Float, initialVelocity: Float) {
        if (abs(target) < 1f) return
        var scrolled = 0f
        TargetBasedAnimation(
            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
            typeConverter = Float.VectorConverter,
            initialValue = 0f,
            targetValue = target,
            initialVelocity = initialVelocity,
        ).runEven(scale = coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f) { value, _ ->
            scrolled += scrollBy(value - scrolled)
            true
        }
    }
}

/** How fast a release has to be, in px/s, to count as a flick one way or the other. */
private const val PAGE_FLING_VELOCITY = 600f
