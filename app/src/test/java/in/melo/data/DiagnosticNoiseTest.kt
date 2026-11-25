package `in`.melo.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Contracts only: which failed statuses reach the diagnostic report, with no network. */
class DiagnosticNoiseTest {
    @Test fun anAnswerTheCallerExpectsIsNotAFailure() {
        // Spotify's 404 for a track without lyrics.
        assertFalse(logsFailure(404, expected = setOf(404)))
    }

    @Test fun everythingElseIsStillReported() {
        assertTrue(logsFailure(500, expected = setOf(404)))
        assertTrue(logsFailure(403, expected = setOf(404)))
        assertTrue(logsFailure(404, expected = emptySet()))
    }
}
