package `in`.melo.playback

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Contracts only: how the pipeline facts are read, with no player and no audio. */
class AudioPipelineTest {
    private val clean = AudioPipeline.Snapshot(
        mimeType = "audio/flac",
        decodedEncoding = C.ENCODING_PCM_16BIT, decodedRateHz = 44_100,
        trackEncoding = C.ENCODING_PCM_16BIT, trackRateHz = 44_100,
    )

    @Test fun untouchedPcmIsBitExact() {
        assertEquals(AudioPipeline.Alteration.None, AudioPipeline.alteration(clean, effectsOn = false))
    }

    @Test fun nothingIsClaimedBeforeTheTrackOpens() {
        val opening = clean.copy(trackEncoding = null)
        assertEquals(AudioPipeline.Alteration.Unknown, AudioPipeline.alteration(opening, effectsOn = false))
    }

    @Test fun theFirstAlteringStageIsNamed() {
        val everything = clean.copy(speed = 1.25f, normalizationGainDb = -3f, volume = 0.5f)
        assertEquals(AudioPipeline.Alteration.Speed, AudioPipeline.alteration(everything, effectsOn = true))
        assertEquals(
            AudioPipeline.Alteration.Normalization,
            AudioPipeline.alteration(everything.copy(speed = 1f), effectsOn = true),
        )
        assertEquals(
            AudioPipeline.Alteration.Volume,
            AudioPipeline.alteration(everything.copy(speed = 1f, normalizationGainDb = 0f), effectsOn = true),
        )
        assertEquals(AudioPipeline.Alteration.Effects, AudioPipeline.alteration(clean, effectsOn = true))
    }

    @Test fun deeperPcmOnAShallowerTrackIsReduced() {
        val hiRes = clean.copy(decodedEncoding = C.ENCODING_PCM_24BIT)
        assertEquals(AudioPipeline.Alteration.DepthReduced, AudioPipeline.alteration(hiRes, effectsOn = false))
        // Float carries 24-bit integer samples whole.
        val float = hiRes.copy(trackEncoding = C.ENCODING_PCM_FLOAT)
        assertEquals(AudioPipeline.Alteration.None, AudioPipeline.alteration(float, effectsOn = false))
    }

    @Test fun aRateChangeIsResampling() {
        val resampled = clean.copy(decodedRateHz = 96_000, trackRateHz = 48_000)
        assertEquals(AudioPipeline.Alteration.Resampled, AudioPipeline.alteration(resampled, effectsOn = false))
    }

    @Test fun onlyLosslessSourcesHaveADepth() {
        assertEquals(24, clean.copy(sourcePcmEncoding = C.ENCODING_PCM_24BIT).sourceBitDepth)
        // An add-on's word stands in when the container says nothing.
        assertEquals(24, clean.copy(claimedBitDepth = 24).sourceBitDepth)
        val opus = clean.copy(mimeType = "audio/opus", claimedBitDepth = 24)
        assertNull(opus.sourceBitDepth)
    }

    @Test fun codecsAreSortedIntoLosslessAndLossy() {
        assertEquals(true, AudioPipeline.losslessMime("audio/flac"))
        assertEquals(false, AudioPipeline.losslessMime("audio/opus"))
        assertEquals(false, AudioPipeline.losslessMime("audio/mp4a-latm"))
        assertNull(AudioPipeline.losslessMime(null))
    }
}
