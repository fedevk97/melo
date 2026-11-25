package `in`.melo.presentation.components

import `in`.melo.data.model.SyncedLine
import `in`.melo.data.model.SyncedWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Contracts only: when a sung word lifts and settles, and which held words swell. */
class LyricMotionTest {
    // "hello" is sung quickly, "there" is held for 2.2 s, "now" closes the line.
    private val line = SweptLine.of(
        SyncedLine(
            1_000,
            "hello there now",
            listOf(SyncedWord(1_000, "hello"), SyncedWord(1_300, "there"), SyncedWord(3_500, "now")),
        ),
        9_000,
    )

    @Test fun aWordLiftsWhileSungAndSettlesAfter() {
        assertEquals(0f, line.wordLift(0, 1_000), 0f)
        assertTrue(line.wordLift(0, 1_200) > 0f)
        // Back on the line once its settle has run.
        assertEquals(0f, line.wordLift(0, 1_300 + RISE_MS.toLong() + 1), 0f)
    }

    @Test fun nothingMovesBeforeTheFirstWord() {
        assertFalse(line.isLifted(999))
        assertTrue(line.isLifted(1_100))
    }

    @Test fun onlyAHeldWordSwells() {
        assertNull(line.growingAt(0))
        assertNotNull(line.growingAt(1))
        assertTrue(line.isGrowing(2_000))
        assertFalse(line.isGrowing(1_100))
    }

    @Test fun theLineStaysStillOnceEverythingHasSettled() {
        val until = line.animatesUntilMs
        assertTrue(until >= line.endMs)
        assertFalse(line.isLifted(until + 1))
    }

    @Test fun theSweepReachesTheEndOfTheLine() {
        assertEquals(line.text.length.toFloat(), line.revealedChars(line.endMs), 0f)
        assertEquals(0f, line.revealedChars(999), 0f)
    }
}
