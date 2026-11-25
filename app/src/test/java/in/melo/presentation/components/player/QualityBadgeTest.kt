package `in`.melo.presentation.components.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Contracts only: which badge a stream description earns. */
class QualityBadgeTest {
    @Test fun cdLosslessIsLossless() =
        assertEquals(QualityBadge.LOSSLESS, qualityBadgeOf("FLAC · 44.1 kHz · 943 kbps", upgrading = false))

    @Test fun aboveCdRateIsHiRes() =
        assertEquals(QualityBadge.HI_RES_LOSSLESS, qualityBadgeOf("FLAC · 96 kHz · 2900 kbps", upgrading = false))

    @Test fun topLossyIsHighQuality() =
        assertEquals(QualityBadge.HIGH, qualityBadgeOf("AAC · 44.1 kHz · 320 kbps", upgrading = false))

    @Test fun ordinaryLossyEarnsNothing() =
        assertNull(qualityBadgeOf("OPUS · 48 kHz · 160 kbps", upgrading = false))

    @Test fun anUpgradeSaysSoUntilLosslessLands() {
        assertEquals(QualityBadge.UPGRADING, qualityBadgeOf("OPUS · 48 kHz · 160 kbps", upgrading = true))
        assertEquals(QualityBadge.HIGH_UPGRADING, qualityBadgeOf("AAC · 44.1 kHz · 320 kbps", upgrading = true))
        assertEquals(QualityBadge.UPGRADING, qualityBadgeOf(null, upgrading = true))
        assertEquals(QualityBadge.LOSSLESS, qualityBadgeOf("FLAC · 44.1 kHz", upgrading = true))
    }
}
