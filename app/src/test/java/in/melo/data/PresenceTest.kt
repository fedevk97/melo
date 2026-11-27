package `in`.melo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PresenceTest {
    @Test fun serverIntervalIsHeldToRange() {
        assertEquals(300, Presence.clampInterval(300))
        assertEquals(60, Presence.clampInterval(5))
        assertEquals(3600, Presence.clampInterval(86_400))
        assertEquals(Presence.DEFAULT_INTERVAL_SEC, Presence.clampInterval(0))
    }

    @Test fun pingsAreSpreadWithinTenPercent() {
        assertEquals(270_000L, Presence.jittered(300, 0.0))
        assertEquals(300_000L, Presence.jittered(300, 0.5))
        assertEquals(330_000L, Presence.jittered(300, 1.0))
        repeat(100) { assertTrue(Presence.jittered(300, Math.random()) in 270_000L..330_000L) }
    }
}
