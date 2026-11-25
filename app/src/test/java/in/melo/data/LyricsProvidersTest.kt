package `in`.melo.data

import `in`.melo.data.preferences.AppSettings
import `in`.melo.data.preferences.lyricsProviderList
import `in`.melo.data.preferences.withLyricsProvider
import `in`.melo.data.repository.LyricsMatching
import `in`.melo.data.repository.LyricsMatching.Candidate
import `in`.melo.data.repository.LyricsProvider
import `in`.melo.data.repository.LyricsQuery
import `in`.melo.utils.LyricsUtils
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LyricsProvidersTest {
    private val query = LyricsQuery("Blinding Lights", "The Weeknd", "After Hours", 200)

    @Test fun recordingMatchRefusesRemixesOtherArtistsAndFarLengths() {
        val hits = listOf(
            Candidate("USUG12004507", "Blinding Lights (Remix)", "The Weeknd, ROSALÍA", 216.1),
            Candidate("XXAAA2000001", "Blinding Lights", "Someone Else", 200.0),
            Candidate("USUG11904999", "Blinding Lights", "The Weeknd", 230.0),
            Candidate("USUG11904206", "Blinding Lights", "The Weeknd", 200.046),
            Candidate("not an isrc", "Blinding Lights", "The Weeknd", 200.0),
        )
        assertEquals("USUG11904206", LyricsMatching.pickRecording(hits, query))
        assertNull(LyricsMatching.pickRecording(hits.take(3), query))
        val remix = query.copy(title = "Blinding Lights (Remix)", seconds = 216)
        assertEquals("USUG12004507", LyricsMatching.pickRecording(hits, remix))
        assertEquals("FRZZZ2400001", LyricsMatching.pickRecording(listOf(Candidate("FRZZZ2400001", "Déjà vu - 2011 Remaster", "Rosé & Bruno Mars", 180.0)),
            LyricsQuery("Deja Vu", "ROSE", "", 181)))
    }

    @Test fun lyricsPlusSyllablesBecomeWordsJoinedWhereTheServiceLeftNoSpace() {
        val body = """{"type":"Word","lyrics":[
            {"time":1000,"duration":900,"text":"e nough","syllabus":[{"time":1000,"duration":200,"text":"e"},{"time":1200,"duration":300,"text":"nough "},{"time":1500,"duration":400,"text":"said"}]},
            {"time":61500,"duration":500,"text":"Line only"},
            {"time":62000,"duration":500,"text":"","syllabus":[]}]}"""
        val lrc = checkNotNull(LyricsMatching.lyricsPlusToLrc(body))
        assertEquals("[00:01.000]<00:01.000>e<00:01.200>nough <00:01.500>said\n[01:01.500]Line only\n", lrc)
        val lines = checkNotNull(LyricsUtils.parseLyrics(lrc).synced)
        assertEquals(listOf(1000, 61500), lines.map { it.time })
        val words = checkNotNull(lines[0].words)
        assertEquals(listOf("e", "nough", "said"), words.map { it.word })
        assertEquals(listOf(true, false, true), words.map { it.startsNewWord })
        assertEquals(listOf(1000, 1200, 1500), words.map { it.time })
        assertNull(lines[1].words)
        assertNull(LyricsMatching.lyricsPlusToLrc("""{"lyrics":[]}"""))
        assertNull(LyricsMatching.lyricsPlusToLrc("not json"))
    }

    @Test fun appleTtmlKeepsAWordTimingForEveryWord() {
        val ttml = """<tt xmlns="http://www.w3.org/ns/ttml" xmlns:itunes="http://music.apple.com/lyric-ttml-internal" itunes:timing="Word"><body><div>""" +
            """<p begin="27.395" end="28.960"><span begin="27.395" end="27.549">I</span> <span begin="27.549" end="27.740">been</span> <span begin="27.740" end="28.1">try</span><span begin="28.1" end="28.9">ing</span></p>""" +
            """<p begin="1:02.5" end="1:04"><span begin="1:02.5" end="1:03">Hey</span></p></div></body></tt>"""
        assertTrue(LyricsMatching.looksLikeTtml(ttml))
        val lines = checkNotNull(LyricsUtils.parseLyrics(ttml).synced)
        assertEquals(listOf(27390, 62500), lines.map { it.time })
        val words = checkNotNull(lines[0].words)
        assertEquals(listOf("I", "been", "try", "ing"), words.map { it.word })
        assertEquals(listOf(true, true, true, false), words.map { it.startsNewWord })
        assertEquals(listOf(27390, 27540, 27740, 28100), words.map { it.time })
    }

    @Test fun kuGouCreditsAndInstrumentalNoticesAreNotSung() {
        val lrc = "[ti:Song]\n[00:00.00]Song - Artist\n[00:01.00]作词：Someone\n[00:02.00]Composer: Other\n" +
            "[00:10.00]First sung line\n[00:12.00]Second: with a colon\n[00:20.00]Last sung line\n[00:30.00]Producer: Them\n"
        assertEquals("[00:10.00]First sung line\n[00:12.00]Second: with a colon\n[00:20.00]Last sung line",
            LyricsMatching.cleanKuGou(lrc)?.replace("\r", ""))
        assertNull(LyricsMatching.cleanKuGou("[00:00.00]纯音乐，请欣赏\n"))
        assertNull(LyricsMatching.cleanKuGou("nothing stamped"))
    }

    @Test fun providerChoicesMigrateFromTheOldSwitchAndRoundTrip() {
        assertEquals(LyricsProvider.entries, AppSettings.fromJson(JSONObject("{}")).lyricsProviderList())
        assertEquals(LyricsProvider.entries, AppSettings.fromJson(JSONObject("{\"lyricsFallback\":true}")).lyricsProviderList())
        val off = AppSettings.fromJson(JSONObject("{\"lyricsFallback\":false}"))
        assertTrue(off.lyricsProviderList().isEmpty())
        val chosen = off.withLyricsProvider(LyricsProvider.LRCLIB, true).withLyricsProvider(LyricsProvider.LRC_RED, true)
        assertEquals(listOf(LyricsProvider.LRC_RED, LyricsProvider.LRCLIB), chosen.lyricsProviderList())
        assertEquals(chosen, AppSettings.fromJson(chosen.toJson()))
        assertEquals(listOf(LyricsProvider.LRCLIB), chosen.withLyricsProvider(LyricsProvider.LRC_RED, false).lyricsProviderList())
    }
}
