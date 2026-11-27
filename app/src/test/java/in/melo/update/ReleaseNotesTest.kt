package `in`.melo.update

import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseNotesTest {
    @Test
    fun readsHeadingsBulletsAndParagraphs() {
        val blocks = ReleaseNotes.parse(
            """
            ## What's new

            - **Smoother tab bar.** The pill stays glass.
            * Plain bullet with a [link](https://example.com).
            ---
            Android 8.0 or later.
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                NoteBlock.Heading("What's new"),
                NoteBlock.Bullet(listOf(NoteSpan("Smoother tab bar.", bold = true), NoteSpan(" The pill stays glass."))),
                NoteBlock.Bullet(listOf(NoteSpan("Plain bullet with a link."))),
                NoteBlock.Paragraph(listOf(NoteSpan("Android 8.0 or later."))),
            ),
            blocks,
        )
    }

    @Test
    fun readsTablesWithoutTheSeparatorRow() {
        val blocks = ReleaseNotes.parse(
            """
            | File | For |
            | --- | :---: |
            | `Melo.apk` | Every device. |
            After.
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                NoteBlock.Table(
                    listOf(
                        listOf(listOf(NoteSpan("File")), listOf(NoteSpan("For"))),
                        listOf(listOf(NoteSpan("Melo.apk", code = true)), listOf(NoteSpan("Every device."))),
                    ),
                ),
                NoteBlock.Paragraph(listOf(NoteSpan("After."))),
            ),
            blocks,
        )
    }

    @Test
    fun theAppLeavesOutTheDownloadSection() {
        val blocks = ReleaseNotes.forApp(
            ReleaseNotes.parse(
                """
                ## What's new
                - Faster.
                ## Which file to download
                | File | For |
                | --- | --- |
                | `Melo.apk` | Every device. |
                Signed with the same key.
                ## Known issues
                - None.
                """.trimIndent(),
            ),
        )
        assertEquals(
            listOf(
                NoteBlock.Heading("What's new"),
                NoteBlock.Bullet(listOf(NoteSpan("Faster."))),
                NoteBlock.Heading("Known issues"),
                NoteBlock.Bullet(listOf(NoteSpan("None."))),
            ),
            blocks,
        )
    }

    @Test
    fun anUnclosedMarkKeepsItsText() {
        assertEquals(listOf(NoteSpan("a `b")), ReleaseNotes.spans("a `b"))
        assertEquals(listOf(NoteSpan("a "), NoteSpan("b", bold = true)), ReleaseNotes.spans("a **b"))
    }
}
