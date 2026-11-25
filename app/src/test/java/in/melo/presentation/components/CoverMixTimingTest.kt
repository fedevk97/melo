package `in`.melo.presentation.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Contracts only: how far the next cover shows and how blurred it is, with no drawing. */
class CoverMixTimingTest {
    @Test fun theRevealRunsFromNothingToAll() {
        assertEquals(0f, CoverMixTiming.reveal(0f), 0f)
        assertEquals(1f, CoverMixTiming.reveal(1f), 1e-6f)
        assertEquals(0.5f, CoverMixTiming.reveal(0.5f), 1e-6f)
        // Out-of-range clocks are held at the ends.
        assertEquals(0f, CoverMixTiming.reveal(-0.2f), 0f)
        assertEquals(1f, CoverMixTiming.reveal(1.4f), 1e-6f)
    }

    @Test fun theRevealOnlyMovesForward() {
        var last = -1f
        for (i in 0..100) {
            val r = CoverMixTiming.reveal(i / 100f)
            assertTrue("step $i went back", r >= last)
            last = r
        }
    }

    @Test fun someOfTheClockIsKeptSoTheStartIsNotFrozen() {
        // Pure smoothstep gives 0.028 at 10 %; a quarter of the linear clock lifts it.
        assertEquals(0.046f, CoverMixTiming.reveal(0.1f), 1e-3f)
    }

    @Test fun theBlurClearsBeforeTheEnd() {
        assertEquals(0, CoverMixTiming.blurStep(0f))
        assertEquals(CoverMixTiming.BLUR_STEPS, CoverMixTiming.blurStep(0.0001f))
        assertEquals(16, CoverMixTiming.blurStep(0.425f))
        assertEquals(0, CoverMixTiming.blurStep(CoverMixTiming.BLUR_END_PROGRESS))
        assertEquals(0, CoverMixTiming.blurStep(1f))
    }
}
