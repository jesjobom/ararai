package com.jesjobom.ararai.ui

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTextTest {
    @Test
    fun `parses supported block markdown`() {
        val blocks = parseMarkdownBlocks(
            """
            # Heading

            Paragraph

            - first
            - second

            1. one
            2. two

            > quoted

            ```kotlin
            val answer = 42
            ```

            ---
            """.trimIndent(),
        )

        assertEquals(MarkdownBlock.Heading(1, "Heading"), blocks[0])
        assertEquals(MarkdownBlock.Paragraph("Paragraph"), blocks[1])
        assertEquals(MarkdownBlock.ListBlock(false, listOf("first", "second")), blocks[2])
        assertEquals(MarkdownBlock.ListBlock(true, listOf("one", "two")), blocks[3])
        assertEquals(MarkdownBlock.Quote("quoted"), blocks[4])
        assertEquals(MarkdownBlock.Code("val answer = 42"), blocks[5])
        assertEquals(MarkdownBlock.Rule, blocks[6])
    }

    @Test
    fun `styles supported inline markdown and preserves link target`() {
        val result = parseInlineMarkdown(
            "Use **bold**, *italic*, `code`, and [docs](https://example.com).",
        )

        assertEquals("Use bold, italic, code, and docs.", result.text)
        assertTrue(result.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
        assertTrue(result.spanStyles.any { it.item.fontStyle == FontStyle.Italic })
        assertEquals("https://example.com", result.getStringAnnotations("URL", 0, result.length).single().item)
    }

    @Test
    fun `preserves malformed markdown as readable text`() {
        val source = "Keep **unfinished and [broken]( link"

        assertEquals(source, parseInlineMarkdown(source).text)
        assertEquals(listOf(MarkdownBlock.Paragraph(source)), parseMarkdownBlocks(source))
    }
}
