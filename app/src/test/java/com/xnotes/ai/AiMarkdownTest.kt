package com.xnotes.ai

import org.junit.Assert.*
import org.junit.Test

class AiMarkdownTest {
    @Test fun rendersStylesAndNestedListNumbers() {
        val blocks = AiMarkdown.parse("# 标题\n\n**粗体** *斜体* ~~删除~~ `code`\n\n3. 三\n4. 四\n   - 内层")
        val prose = blocks.filterIsInstance<AiMarkdown.Block.Prose>()
        assertEquals(1, prose.first().paragraph.headingLevel)
        val styles = prose.flatMap { it.paragraph.runs }.map { it.style }
        assertTrue(styles.any { it.bold }); assertTrue(styles.any { it.italic })
        assertTrue(styles.any { it.strike }); assertTrue(styles.any { it.code })
        assertTrue(prose.any { it.marker == "3." }); assertTrue(prose.any { it.marker == "4." })
        assertTrue(prose.any { it.marker == "•" })
    }
    @Test fun unfinishedFenceKeepsLiteralCodeAndCompletesDuringStreaming() {
        val partial = AiMarkdown.parse("```kotlin\nval x = \"**literal**\"\nnext")
        val code = partial.filterIsInstance<AiMarkdown.Block.Code>().single()
        assertEquals("val x = \"**literal**\"\nnext", code.text)
        val complete = AiMarkdown.parse("```kotlin\nval x = 1\n```\n\nDone")
        assertEquals("val x = 1", complete.filterIsInstance<AiMarkdown.Block.Code>().single().text)
        assertTrue(complete.filterIsInstance<AiMarkdown.Block.Prose>().any { it.paragraph.plainText() == "Done" })
    }
    @Test fun groupsTableCellsWithoutLosingInlineStyles() {
        val table = AiMarkdown.parse("| A | B |\n| --- | --- |\n| **bold** | two |").filterIsInstance<AiMarkdown.Block.Table>().single()
        assertEquals(2, table.rows.size); assertEquals(2, table.rows.first().size)
        assertEquals("two", table.rows[1][1].single().plainText())
        assertTrue(table.rows[1][0].single().runs.first().style.bold)
    }
    @Test fun untrustedHtmlAndImagesStayTextAndUnsafeLinksCannotOpen() {
        val text = "[safe](https://example.org) [bad](javascript:alert) ![alt](https://example.org/track.png)\n\n<script>alert(1)</script>"
        val runs = AiMarkdown.parse(text).filterIsInstance<AiMarkdown.Block.Prose>().flatMap { it.paragraph.runs }
        assertEquals(listOf("https://example.org"), runs.mapNotNull { it.style.link })
        assertTrue(runs.joinToString("") { it.text }.contains("alt"))
        assertTrue(runs.joinToString("") { it.text }.contains("<script>"))
    }
    @Test fun tasksQuotesAndEveryStreamingPrefixRemainReadable() {
        val source = "> 引用\n\n- [x] 完成\n- [ ] 待办\n\n**加粗** 与 [链接](https://example.org)"
        for (length in 0..source.length) AiMarkdown.parse(source.take(length))
        val prose = AiMarkdown.parse(source).filterIsInstance<AiMarkdown.Block.Prose>()
        assertTrue(prose.any { it.paragraph.quote != null })
        assertTrue(prose.any { it.marker == "☑" })
        assertTrue(prose.any { it.marker == "☐" })
        assertTrue(AiMarkdown.parse("").isEmpty())
    }
}
