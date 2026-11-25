package `in`.melo.presentation.components

import `in`.melo.data.model.SyncedLine
import `in`.melo.data.model.SyncedWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Contracts only: which lines a share card takes, and the stretches of the song its video plays. */
class LyricsShareTest {
    private fun synced(vararg starts: Int): List<PickableLine> {
        val lines = starts.mapIndexed { i, t -> SyncedLine(t, "line $i") }
        return lines.mapIndexed { i, line ->
            PickableLine(line.line, null, SweptLine.of(line, lines.getOrNull(i + 1)?.time ?: Int.MAX_VALUE))
        }
    }

    @Test fun theCardHoldsTwoHundredFiftyCharacters() {
        assertTrue(fitsOnCard(0, "a".repeat(250)))
        assertFalse(fitsOnCard(0, "a".repeat(251)))
        assertTrue(fitsOnCard(200, "a".repeat(50)))
        assertFalse(fitsOnCard(200, "a".repeat(51)))
    }

    @Test fun skippedLinesBecomeOneGapMarker() {
        val lines = (0..5).map { PickableLine("line $it", null, null) }
        val shared = buildShareLines(lines, setOf(4, 0, 1))
        assertEquals(listOf("line 0", "line 1", "", "line 4"), shared.map { it.text })
        assertEquals(listOf(false, false, true, false), shared.map { it.isGap })
        // Plain lyrics carry no timing.
        assertTrue(shared.all { it.startMs == null })
    }

    @Test fun aRunOfPickedLinesPlaysAsOneTake() {
        val lines = synced(1_000, 4_000, 8_000, 12_000)
        // Line 0 runs on to line 1, which is picked too.
        assertEquals(1_000L to 4_000L, singWindow(lines, 0, nextPicked = 1))
        // Line 1 ends a run: no word timings, so it holds until the next line starts.
        assertEquals(4_000L to 8_000L, singWindow(lines, 1, nextPicked = null))
    }

    @Test fun aLineWithWordsEndsABeatAfterItsLastWord() {
        val words = listOf(SyncedWord(1_000, "hello"), SyncedWord(1_500, "there"))
        val line = SyncedLine(1_000, "hello there", words)
        val next = SyncedLine(9_000, "after")
        val pickable = listOf(
            PickableLine(line.line, null, SweptLine.of(line, next.time)),
            PickableLine(next.line, null, SweptLine.of(next, Int.MAX_VALUE)),
        )
        val (start, end) = singWindow(pickable, 0, nextPicked = null)!!
        assertEquals(1_000L, start)
        // The last word is swept for at most 1.2 s, then 0.4 s of tail.
        assertEquals(1_500L + 1_200L + 400L, end)
    }

    @Test fun theLastLineIsHeldForAFallback() {
        val lines = synced(1_000)
        assertEquals(1_000L to 6_000L, singWindow(lines, 0, nextPicked = null))
    }

    @Test fun theTimelineJoinsRunsAndCutsAtGaps() {
        val lines = synced(1_000, 4_000, 8_000, 12_000, 20_000)
        val shared = buildShareLines(lines, setOf(0, 1, 3))
        val timeline = shareTimeline(shared)!!
        // 0 and 1 are one stretch, the gap cuts to 3.
        assertEquals(listOf(1_000L to 8_000L, 12_000L to 20_000L), timeline.segments)
        assertEquals(15_000L, timeline.durationMs)
        // The video's clock maps back onto the song across the cut.
        assertEquals(1_000L, timeline.trackMsAt(0))
        assertEquals(7_999L, timeline.trackMsAt(6_999_000))
        assertEquals(12_000L, timeline.trackMsAt(7_000_000))
        assertEquals(20_000L, timeline.trackMsAt(99_000_000))
    }

    @Test fun theVideoStopsAtOneMinute() {
        val lines = synced(0, 50_000, 100_000)
        val timeline = shareTimeline(buildShareLines(lines, setOf(0, 1, 2)))!!
        assertEquals(MAX_SHARE_MS, timeline.durationMs)
    }

    @Test fun plainLyricsHaveNoTimeline() {
        val lines = (0..2).map { PickableLine("line $it", null, null) }
        assertNull(shareTimeline(buildShareLines(lines, setOf(0, 1))))
    }

    @Test fun theActiveLineIsTheLastToHaveStarted() {
        val shared = buildShareLines(synced(1_000, 4_000, 8_000), setOf(0, 2))
        // 0, gap, 2
        assertEquals(0, activeShareLine(shared, 0))
        assertEquals(0, activeShareLine(shared, 5_000))
        assertEquals(2, activeShareLine(shared, 8_000))
    }
}
