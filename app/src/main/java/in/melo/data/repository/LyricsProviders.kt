package `in`.melo.data.repository

import androidx.annotation.StringRes
import `in`.melo.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.Base64
import java.util.Locale
import kotlin.math.abs

/**
 * The third-party lyrics services, in their default order. Each one receives the
 * title, artist, album and duration of the track, and nothing else.
 * [wordTimed] says whether it can return a timing for every word.
 */
enum class LyricsProvider(val key: String, val label: String, @StringRes val detail: Int, val wordTimed: Boolean) {
    LRC_RED("lrcred", "lrc.red", R.string.lyrics_provider_lrcred, true),
    BETTER_LYRICS("betterlyrics", "BetterLyrics", R.string.lyrics_provider_betterlyrics, true),
    LYRICS_PLUS("lyricsplus", "LyricsPlus", R.string.lyrics_provider_lyricsplus, true),
    UNISON("unison", "Unison", R.string.lyrics_provider_unison, true),
    LRCLIB("lrclib", "LRCLIB", R.string.lyrics_provider_lrclib, false),
    KUGOU("kugou", "KuGou", R.string.lyrics_provider_kugou, false);

    companion object {
        fun of(key: String): LyricsProvider? = entries.firstOrNull { it.key == key }
    }
}

/** What a provider is asked: names as the catalogue shows them and a length in whole seconds. */
data class LyricsQuery(val title: String, val artist: String, val album: String, val seconds: Int)

/**
 * The requests behind [LyricsProvider]. Every answer is returned as the raw document
 * (Apple-style TTML, enhanced LRC with word stamps, plain LRC or plain text), which
 * [in.melo.utils.LyricsUtils.parseLyrics] reads, so the saved copy keeps its word timings.
 */
internal class LyricsProviders(private val client: OkHttpClient) {
    @Volatile private var lyricsPlusHost: String? = null

    suspend fun fetch(provider: LyricsProvider, query: LyricsQuery): String? = when (provider) {
        LyricsProvider.LRC_RED -> lrcRed(query)
        LyricsProvider.BETTER_LYRICS -> betterLyrics(query)
        LyricsProvider.LYRICS_PLUS -> lyricsPlus(query)
        LyricsProvider.UNISON -> unison(query)
        LyricsProvider.LRCLIB -> null // Asked by LyricsRepository, which also offers its search results.
        LyricsProvider.KUGOU -> kuGou(query)
    }

    /** Apple-timed TTML filed by ISRC: a search for the recording, then its document. */
    private suspend fun lrcRed(query: LyricsQuery): String? {
        val search = "https://lrc.red/search.json".toHttpUrl().newBuilder()
            .addQueryParameter("q", "${query.title} ${query.artist}".trim()).build()
        val hits = get(search.toString())?.let { runCatching { JSONObject(it).optJSONArray("hits") }.getOrNull() } ?: return null
        val isrc = LyricsMatching.pickRecording(
            (0 until hits.length()).map { hits.getJSONObject(it) }.map {
                LyricsMatching.Candidate(it.optString("isrc"), it.optString("title"), it.optString("artist"), it.optDouble("duration", -1.0))
            },
            query,
        ) ?: return null
        return get("https://lrc.red/s/$isrc.ttml")?.takeIf(LyricsMatching::looksLikeTtml)
    }

    private suspend fun betterLyrics(query: LyricsQuery): String? {
        val url = "https://lyrics-api.boidu.dev/getLyrics".toHttpUrl().newBuilder()
            .addQueryParameter("s", query.title).addQueryParameter("a", query.artist)
            .apply {
                if (query.seconds > 0) addQueryParameter("d", query.seconds.toString())
                if (query.album.isNotBlank()) addQueryParameter("al", query.album)
            }.build()
        val body = get(url.toString()) ?: return null
        return runCatching { JSONObject(body).optString("ttml") }.getOrNull()?.takeIf(LyricsMatching::looksLikeTtml)
    }

    /**
     * Community mirrors of one backend, most of them down at any moment: the one that answered last
     * time is asked alone, then the others all at once, and the first real answer is kept.
     */
    private suspend fun lyricsPlus(query: LyricsQuery): String? {
        suspend fun ask(host: String): String? {
            val url = "$host/v2/lyrics/get".toHttpUrl().newBuilder()
                .addQueryParameter("title", query.title).addQueryParameter("artist", query.artist)
                .apply {
                    if (query.seconds > 0) addQueryParameter("duration", query.seconds.toString())
                    if (query.album.isNotBlank()) addQueryParameter("album", query.album)
                }.build()
            return get(url.toString())?.let(LyricsMatching::lyricsPlusToLrc)
        }
        lyricsPlusHost?.let { host -> ask(host)?.let { return it } }
        return withTimeoutOrNull(8_000) {
            channelFlow {
                LYRICS_PLUS_MIRRORS.filter { it != lyricsPlusHost }.forEach { host ->
                    launch { ask(host)?.let { send(host to it) } }
                }
            }.firstOrNull()
        }?.let { (host, lrc) -> lyricsPlusHost = host; lrc }
    }

    private suspend fun unison(query: LyricsQuery): String? {
        val url = "https://unison.boidu.dev/lyrics".toHttpUrl().newBuilder()
            .addQueryParameter("song", query.title).addQueryParameter("artist", query.artist)
            .apply {
                if (query.album.isNotBlank()) addQueryParameter("album", query.album)
                if (query.seconds > 0) addQueryParameter("duration", query.seconds.toString())
            }.build()
        val json = get(url.toString())?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return null
        if (!json.optBoolean("success")) return null
        return json.optJSONObject("data")?.optString("lyrics")?.takeIf { it.isNotBlank() && it != "null" }
    }

    /** A lyrics search near the track's length, then the base64 LRC of its first candidate. */
    private suspend fun kuGou(query: LyricsQuery): String? {
        val keyword = "${LyricsMatching.withoutBrackets(query.title)} - ${LyricsMatching.withoutBrackets(query.artist)}"
        val search = "https://lyrics.kugou.com/search".toHttpUrl().newBuilder()
            .addQueryParameter("ver", "1").addQueryParameter("man", "yes").addQueryParameter("client", "pc")
            .addQueryParameter("keyword", keyword)
            .apply { if (query.seconds > 0) addQueryParameter("duration", (query.seconds * 1000L).toString()) }
            .build()
        val candidate = get(search.toString())
            ?.let { runCatching { JSONObject(it).optJSONArray("candidates")?.optJSONObject(0) }.getOrNull() } ?: return null
        val download = "https://lyrics.kugou.com/download".toHttpUrl().newBuilder()
            .addQueryParameter("ver", "1").addQueryParameter("client", "pc").addQueryParameter("fmt", "lrc")
            .addQueryParameter("charset", "utf8").addQueryParameter("id", candidate.optString("id"))
            .addQueryParameter("accesskey", candidate.optString("accesskey")).build()
        val content = get(download.toString())?.let { runCatching { JSONObject(it).optString("content") }.getOrNull() } ?: return null
        val lrc = runCatching { String(Base64.getDecoder().decode(content), Charsets.UTF_8) }.getOrNull() ?: return null
        return LyricsMatching.cleanKuGou(lrc)
    }

    private suspend fun get(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(Request.Builder().url(url).header("User-Agent", "Melo").header("Accept", "application/json").build())
                .execute().use { response -> if (response.isSuccessful) response.body?.string() else null }
        }.getOrNull()
    }

    private companion object {
        val LYRICS_PLUS_MIRRORS = listOf(
            "https://lyricsplus.prjktla.my.id",
            "https://lyricsplus.atomix.one",
            "https://lyricsplus.binimum.org",
            "https://lyricsplus.prjktla.workers.dev",
            "https://lyricsplus-seven.vercel.app",
            "https://lyrics-plus-backend.vercel.app",
        )
    }
}

/** The parts of the providers that need no network, kept apart so they can be tested. */
internal object LyricsMatching {
    data class Candidate(val isrc: String, val title: String, val artist: String, val seconds: Double)

    private val ISRC = Regex("[A-Z]{2}[A-Z0-9]{3}\\d{7}")
    private val BRACKETS = Regex("[(\\[（][^)\\]）]*[)\\]）]")
    private val ARTIST_SPLIT = Regex("\\s*(?:,|&|;|/|\\s+and\\s+|\\s+x\\s+|\\s+feat\\.?\\s+|\\s+ft\\.?\\s+|\\s+with\\s+)\\s*", RegexOption.IGNORE_CASE)

    /** Words that make a bracket or a " - " suffix name a different recording, not a reissue. */
    private val VERSION_WORDS = setOf(
        "live", "remix", "mix", "acoustic", "unplugged", "instrumental", "karaoke", "cappella", "acapella",
        "demo", "edit", "version", "cover", "sped", "slowed", "reverb", "nightcore", "extended", "orchestral",
    )

    /**
     * The ISRC of the search hit that is this recording, or null when none clearly is: same title outside
     * brackets, the same version words, a shared artist and a length within three seconds.
     * Of those, the one closest in length.
     */
    fun pickRecording(hits: List<Candidate>, query: LyricsQuery): String? {
        val title = core(query.title)
        val version = versionWords(query.title)
        val artists = artists(query.artist)
        fun gap(hit: Candidate) = if (query.seconds > 0 && hit.seconds > 0) abs(hit.seconds - query.seconds) else 0.0
        return hits.filter { hit ->
            ISRC.matches(hit.isrc) && core(hit.title) == title && versionWords(hit.title) == version &&
                (artists.isEmpty() || artists(hit.artist).any { it in artists }) && gap(hit) <= 3.0
        }.minByOrNull(::gap)?.isrc
    }

    fun withoutBrackets(value: String): String = value.replace(BRACKETS, "").trim().ifEmpty { value }

    fun looksLikeTtml(value: String): Boolean = value.contains("<tt", ignoreCase = true) && value.contains("<p", ignoreCase = true)

    /**
     * LyricsPlus' JSON as enhanced LRC: each line stamped, each syllable stamped, and spaces kept
     * where the service put them, since they are what joins syllables into words.
     */
    fun lyricsPlusToLrc(body: String): String? {
        val lines = runCatching { JSONObject(body).optJSONArray("lyrics") }.getOrNull() ?: return null
        val out = buildString {
            for (index in 0 until lines.length()) {
                val line = lines.optJSONObject(index) ?: continue
                if (!line.has("time")) continue
                val start = line.optLong("time")
                val syllables = line.optJSONArray("syllabus") ?: JSONArray()
                val body = if (syllables.length() > 0) buildString {
                    for (s in 0 until syllables.length()) {
                        val syllable = syllables.optJSONObject(s) ?: continue
                        val text = syllable.optString("text").replace('\n', ' ')
                        if (text.isBlank()) { if (text.isNotEmpty()) append(' '); continue }
                        append('<').append(stamp(syllable.optLong("time", start))).append('>').append(text)
                    }
                } else line.optString("text").replace('\n', ' ')
                if (body.isBlank()) continue
                append('[').append(stamp(start)).append(']').append(body.trimEnd()).append('\n')
            }
        }
        return out.takeIf { it.isNotBlank() }
    }

    private val STAMPED = Regex("^\\[\\d{2}:\\d{2}[.:]\\d{2,3}](.*)$")
    private val CREDIT = Regex("^[^:：]{1,24}[:：].+")

    /**
     * KuGou opens and closes its files with credits (lyricist, composer…) stamped as if sung,
     * and answers an instrumental with a single "pure music" notice. Both are removed; a file
     * with nothing else is no lyrics at all.
     */
    fun cleanKuGou(lrc: String): String? {
        val lines = lrc.lines().map(String::trim).filter { STAMPED.matches(it) }
        fun text(line: String) = STAMPED.matchEntire(line)!!.groupValues[1].trim()
        var from = 0
        // The opening run only: a title line, then credits. A sung line ends it.
        for (index in 0 until minOf(12, lines.size)) {
            if (CREDIT.matches(text(lines[index]))) from = index + 1 else if (index > 0) break
        }
        var until = lines.size
        for (index in lines.indices.reversed().take(6)) { if (index >= from && CREDIT.matches(text(lines[index]))) until = index else break }
        val kept = if (from < until) lines.subList(from, until) else emptyList()
        val sung = kept.map(::text).filter(String::isNotEmpty)
        if (sung.isEmpty() || sung.all { it.contains("纯音乐") }) return null
        return kept.joinToString("\n")
    }

    private fun stamp(ms: Long): String {
        val safe = ms.coerceAtLeast(0)
        return String.format(Locale.ROOT, "%02d:%02d.%03d", safe / 60_000, safe / 1000 % 60, safe % 1000)
    }

    private fun core(title: String): String = normalized(title.replace(BRACKETS, " ").substringBefore(" - ")).ifEmpty { normalized(title) }

    private fun versionWords(title: String): Set<String> {
        val extras = BRACKETS.findAll(title).joinToString(" ") { it.value } + " " + title.substringAfter(" - ", "")
        return normalized(extras).split(' ').filter { it in VERSION_WORDS }.toSet()
    }

    private fun artists(value: String): Set<String> = value.split(ARTIST_SPLIT).map(::normalized).filter(String::isNotEmpty).toSet()

    private fun normalized(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "").lowercase(Locale.ROOT)
        .map { if (it.isLetterOrDigit()) it else ' ' }.joinToString("")
        .replace(Regex("\\s+"), " ").trim()
}
