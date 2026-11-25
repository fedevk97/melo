package `in`.melo.data.addons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Contracts only: what Melo reads from an add-on's answers, with no network. */
class AddonTest {
    private fun manifest(json: String) = AddonClient.JSON.decodeFromString(AddonManifest.serializer(), json)
    private fun stream(json: String) = AddonClient.JSON.decodeFromString(AddonStream.serializer(), json)

    @Test fun manifestReadsFlagsAndQualityChoices() {
        val m = manifest(
            """{"id":"x","name":"Catalogue","version":"1.2","resources":["search","stream"],
               "allowDownloads":0,"checkValidLossless":"1",
               "settings":[{"key":"quality","type":"select","default":"max",
                 "options":[{"value":"max","label":"Max"},{"value":"high","label":"High"},{"value":"low","label":"Low"}]}],
               "somethingNew":{"a":1}}""",
        )
        assertTrue(m.isPlayable)
        assertFalse(m.downloadsAllowed)
        assertTrue(m.requiresLosslessOutput)
        assertFalse(m.requiresAttestation)
        assertEquals(listOf("max", "high", "low"), m.qualityOptions.map { it.value })
        assertEquals("Catalogue", m.displayName)
    }

    @Test fun manifestWithoutSearchIsNotPlayableAndMissingFlagsDefault() {
        val m = manifest("""{"id":"only-meta","resources":["catalog"]}""")
        assertFalse(m.isPlayable)
        assertTrue(m.downloadsAllowed)
        assertFalse(m.requiresLosslessOutput)
        assertEquals("only-meta", m.displayName)
        assertTrue(manifest("""{"id":"y"}""").isPlayable)
    }

    @Test fun dashAnswerIsReadAsDashFlac() {
        val s = stream(
            """{"url":"https://cdn.example/m/1.mpd","format":"dash","quality":"Lossless · 16-bit / 44.1 kHz · 892 kbps",
               "codec":"flac","container":"mp4","manifest":"dash","encrypted":false,
               "sampleRate":44100,"bitDepth":16,"bitrate":891855}""",
        )
        assertEquals(StreamTransport.Dash, s.transport)
        assertFalse(s.isEncrypted)
        val format = StreamFormat.of(s)
        assertEquals(true, format.isLossless)
        assertEquals(16, format.bitDepth)
        assertEquals(44_100, format.sampleRateHz)
    }

    @Test fun transportFollowsTheAddressWhenUndeclared() {
        assertEquals(StreamTransport.Hls, stream("""{"url":"https://h/x/list.m3u8?sig=1"}""").transport)
        assertEquals(StreamTransport.Dash, stream("""{"url":"https://h/x.mpd"}""").transport)
        assertEquals(StreamTransport.File, stream("""{"url":"https://h/x.flac","format":"flac"}""").transport)
    }

    @Test fun anyNamedSchemeCountsAsEncrypted() {
        assertTrue(stream("""{"url":"https://h/a","encrypted":"widevine"}""").isEncrypted)
        assertTrue(stream("""{"url":"https://h/a","encrypted":true}""").isEncrypted)
        assertFalse(stream("""{"url":"https://h/a","encrypted":"none"}""").isEncrypted)
        assertFalse(stream("""{"url":"https://h/a"}""").isEncrypted)
    }

    @Test fun lossyCodecIsNotLossless() {
        assertEquals(false, StreamFormat.of(stream("""{"url":"https://h/a","codec":"aac","bitrate":320000}""")).isLossless)
        assertNull(StreamFormat.of(stream("""{"url":"https://h/a"}""")).isLossless)
    }

    @Test fun losslessBeatsLossyAndHigherResolutionWins() {
        val cd = StreamFormat(true, 16, 44_100, 900)
        val hiRes = StreamFormat(true, 24, 96_000, 3000)
        val lossy = StreamFormat(false, null, 44_100, 320)
        assertTrue(cd.beats(lossy))
        assertFalse(lossy.beats(cd))
        assertTrue(hiRes.beats(cd))
        assertTrue(cd.beats(null))
    }

    @Test fun tooShortCatchesPreviewClips() {
        val lossless = StreamFormat(true, 16, 44_100, null)
        // 30 s of CD audio passed off as a 4-minute track.
        assertTrue(AddonResolver.tooShort(30L * 900_000 / 8, lossless, expectedSec = 240, slackSec = 5))
        // A whole FLAC track at a low lossless rate.
        assertFalse(AddonResolver.tooShort(240L * 600_000 / 8, lossless, expectedSec = 240, slackSec = 5))
        // Nothing known: never refused.
        assertFalse(AddonResolver.tooShort(0, lossless, expectedSec = 240, slackSec = 5))
    }

    @Test fun rowHintsTellQualityAndAtmosApart() {
        val hiRes = AddonTrack(id = "1", title = "t", format = "flac", audioQuality = "HI_RES_LOSSLESS")
        val cd = AddonTrack(id = "2", title = "t", format = "flac", audioQuality = "LOSSLESS")
        val atmos = AddonTrack(id = "3", title = "t", audioModes = listOf("DOLBY_ATMOS"))
        assertTrue(hiRes.isHiRes && hiRes.isLossless)
        assertTrue(!cd.isHiRes && cd.isLossless)
        assertTrue(atmos.isDolbyAtmos)
        assertFalse(cd.isDolbyAtmos)
        assertEquals(240, AddonTrack(duration = 240.6).durationSec)
    }

    @Test fun matcherPrefersTheSameRecording() {
        val target = AddonMatcher.Target(title = "Blinding Lights", artist = "The Weeknd", durationSec = 200)
        val rows = listOf(
            AddonTrack(id = "cover", title = "Blinding Lights", artist = "Tribute Band", duration = 200.0),
            AddonTrack(id = "remix", title = "Blinding Lights (Chromatics Remix)", artist = "The Weeknd", duration = 389.0),
            AddonTrack(id = "real", title = "Blinding Lights", artist = "The Weeknd", duration = 201.0),
        )
        assertEquals("real", AddonMatcher.ranked(rows, target).first().id)
        assertTrue(AddonMatcher.queries(target).isNotEmpty())
    }

    @Test fun addonIdsRoundTrip() {
        val id = AddonUris.trackId("abc123", "t_408871384")
        assertTrue(AddonUris.isAddonTrack(id))
        assertEquals("abc123" to "t_408871384", AddonUris.split(id))
        assertNull(AddonUris.split("addon:abc123"))
        assertNull(AddonUris.split("spotify:track:x"))
        assertEquals(`in`.melo.data.MusicSource.Addon, `in`.melo.data.musicSourceOf(id))
    }

    @Test fun pastedAddressesAreNormalisedAndRedacted() {
        assertEquals("https://h.example/a/key/all", AddonClient.normalizeBase(" https://h.example/a/key/all/manifest.json "))
        assertEquals("https://h.example/a", AddonClient.normalizeBase("https://h.example/a/"))
        assertEquals("https://h.example/***", AddonClient.redact("https://h.example/a/secret/all/manifest.json"))
    }
}
