package `in`.melo.playback

import `in`.melo.data.addons.StreamFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Contracts only: when a playing track is worth switching to another copy, with no player. */
class LosslessUpgradeTest {
    private val flac = StreamFormat(isLossless = true, bitDepth = 16, sampleRateHz = 44_100, kbps = null)

    @Test fun losslessIsAlwaysWorthASwitch() {
        assertTrue(LosslessUpgrade.worthSwapping(flac, playingKbps = 160))
        assertTrue(LosslessUpgrade.worthSwapping(flac, playingKbps = null))
    }

    @Test fun lossyHasToClearTheMinimumGain() {
        val aac320 = StreamFormat(false, null, 44_100, 320)
        val aac192 = StreamFormat(false, null, 44_100, 192)
        assertTrue(LosslessUpgrade.worthSwapping(aac320, playingKbps = 160))
        assertFalse(LosslessUpgrade.worthSwapping(aac192, playingKbps = 160))
        // Unmeasured YouTube audio is taken at its usual rate, not as nothing.
        assertTrue(LosslessUpgrade.worthSwapping(aac320, playingKbps = null))
        // A copy that states nothing about itself is not worth a break in the audio.
        assertFalse(LosslessUpgrade.worthSwapping(StreamFormat(null, null, null, null), playingKbps = 160))
    }

    @Test fun sameRecordingMeansSameLengthWithinTolerance() {
        assertTrue(LosslessUpgrade.sameRecording(203, 200, slackSec = 5))
        assertFalse(LosslessUpgrade.sameRecording(189, 163, slackSec = 5))
        assertFalse(LosslessUpgrade.sameRecording(null, 200, slackSec = 5))
    }

    @Test fun theDecoderLengthWinsUnlessItIsAnotherCut() {
        assertEquals(201, LosslessUpgrade.targetSec(catalogueSec = 200, playingSec = 201))
        // A 3:29 video edit playing for a 5:02 album track: the album length is the one wanted.
        assertEquals(302, LosslessUpgrade.targetSec(catalogueSec = 302, playingSec = 209))
        assertEquals(200, LosslessUpgrade.targetSec(catalogueSec = 200, playingSec = null))
    }

    @Test fun tooLittleLeftIsNotSwitched() {
        assertTrue(LosslessUpgrade.enoughLeft(positionMs = 30_000, durationMs = 200_000))
        assertFalse(LosslessUpgrade.enoughLeft(positionMs = 190_000, durationMs = 200_000))
        assertTrue(LosslessUpgrade.enoughLeft(positionMs = 0, durationMs = 0))
    }

    @Test fun theMarkerRoundTrips() {
        val track = "ytm://track/abc"
        val match = "ytm://match/spotify:track:x?title=T&duration=200000"
        assertEquals("ytm://track/abc?upgrade=1", LosslessUpgrade.markedUri(track))
        assertEquals(track, LosslessUpgrade.unmarkedUri(LosslessUpgrade.markedUri(track)))
        assertEquals("$match&upgrade=1", LosslessUpgrade.markedUri(match))
        assertEquals(match, LosslessUpgrade.unmarkedUri(LosslessUpgrade.markedUri(match)))
    }
}
