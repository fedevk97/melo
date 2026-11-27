package `in`.melo.update

/**
 * A release's notes, read from the small part of Markdown that GitHub release bodies use:
 * headings, bullets, paragraphs and tables, with bold, code and links inside a line.
 */
internal sealed interface NoteBlock {
    data class Heading(val text: String) : NoteBlock
    data class Bullet(val spans: List<NoteSpan>) : NoteBlock
    data class Paragraph(val spans: List<NoteSpan>) : NoteBlock
    /** The header row first; the `---` separator row is dropped. */
    data class Table(val rows: List<List<List<NoteSpan>>>) : NoteBlock
}

internal data class NoteSpan(val text: String, val bold: Boolean = false, val code: Boolean = false)

internal object ReleaseNotes {
    private const val MAX_LINES = 160
    private val bulletPrefix = Regex("^[-*+]\\s+")
    private val separatorCell = Regex("^:?-{3,}:?$")
    private val link = Regex("\\[([^\\]]+)]\\([^)]+\\)")

    fun parse(markdown: String): List<NoteBlock> {
        val blocks = ArrayList<NoteBlock>()
        var table: MutableList<List<List<NoteSpan>>>? = null
        fun closeTable() {
            table?.takeIf { it.isNotEmpty() }?.let { blocks += NoteBlock.Table(it) }
            table = null
        }
        markdown.lineSequence().take(MAX_LINES).map(String::trim).forEach { line ->
            if (line.startsWith('|')) {
                val cells = line.removePrefix("|").removeSuffix("|").split('|').map(String::trim)
                if (cells.all { separatorCell.matches(it) }) return@forEach
                (table ?: ArrayList<List<List<NoteSpan>>>().also { table = it }) += cells.map(::spans)
                return@forEach
            }
            closeTable()
            when {
                line.isBlank() || line == "---" || line.startsWith("<!--") -> Unit
                line.startsWith('#') -> blocks += NoteBlock.Heading(plain(line.trimStart('#').trim()))
                bulletPrefix.containsMatchIn(line) -> blocks += NoteBlock.Bullet(spans(line.replaceFirst(bulletPrefix, "")))
                else -> blocks += NoteBlock.Paragraph(spans(line))
            }
        }
        closeTable()
        return blocks
    }

    private val downloadHeading = Regex("download|télécharg", RegexOption.IGNORE_CASE)

    /**
     * The notes as the app shows them: a section about which file to download is for the
     * release page, since the updater picks the file itself.
     */
    fun forApp(blocks: List<NoteBlock>): List<NoteBlock> {
        var skipping = false
        return blocks.filter { block ->
            if (block is NoteBlock.Heading) skipping = downloadHeading.containsMatchIn(block.text)
            !skipping
        }
    }

    /** Splits a line into plain, `**bold**` and `` `code` `` runs; links keep only their text. */
    fun spans(line: String): List<NoteSpan> {
        val text = link.replace(line) { it.groupValues[1] }
        val result = ArrayList<NoteSpan>()
        val run = StringBuilder()
        var bold = false
        var i = 0
        fun flush() {
            if (run.isNotEmpty()) result += NoteSpan(run.toString(), bold = bold)
            run.clear()
        }
        while (i < text.length) {
            when {
                text.startsWith("**", i) -> { flush(); bold = !bold; i += 2 }
                text[i] == '`' && text.indexOf('`', i + 1) > i -> {
                    flush()
                    val end = text.indexOf('`', i + 1)
                    result += NoteSpan(text.substring(i + 1, end), bold = bold, code = true)
                    i = end + 1
                }
                else -> { run.append(text[i]); i++ }
            }
        }
        flush()
        return result
    }

    private fun plain(line: String) = spans(line).joinToString("") { it.text }
}
