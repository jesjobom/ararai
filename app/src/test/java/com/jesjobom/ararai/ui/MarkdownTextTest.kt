package com.jesjobom.ararai.ui

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTextTest {
    @Test
    fun `parses supported block markdown`() {
        val blocks =
            parseMarkdownBlocks(
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
        val result =
            parseInlineMarkdown(
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

    @Test
    fun `parses display math delimiters as blocks`() {
        val blocks =
            parseMarkdownBlocks(
                """
                Before

                ${'$'}${'$'}
                C = D \times \frac{360^\circ}{\Delta \theta}
                ${'$'}${'$'}

                \[E = mc^2\]
                """.trimIndent(),
            )

        assertEquals(MarkdownBlock.Paragraph("Before"), blocks[0])
        assertEquals(
            MarkdownBlock.Math(
                latex = "C = D \\times \\frac{360^\\circ}{\\Delta \\theta}",
                source = "${'$'}${'$'}\nC = D \\times \\frac{360^\\circ}{\\Delta \\theta}\n${'$'}${'$'}",
            ),
            blocks[1],
        )
        assertEquals(MarkdownBlock.Math("E = mc^2", "\\[E = mc^2\\]"), blocks[2])
    }

    @Test
    fun `parses inline math while preserving text`() {
        val segments = parseInlineMath("Angle ${'$'}\\theta${'$'} and \\(x^2\\).")

        assertEquals(
            listOf(
                MathInlineSegment.Text("Angle "),
                MathInlineSegment.Math("\\theta", "${'$'}\\theta${'$'}"),
                MathInlineSegment.Text(" and "),
                MathInlineSegment.Math("x^2", "\\(x^2\\)"),
                MathInlineSegment.Text("."),
            ),
            segments,
        )
    }

    @Test
    fun `does not consume currency escaped or incomplete math`() {
        val source = "Costs ${'$'}20 and ${'$'}30; escaped \\${'$'}x; streaming ${'$'}\\frac{1}{2"

        assertEquals(listOf(MathInlineSegment.Text(source)), parseInlineMath(source))
        assertEquals(
            listOf(MarkdownBlock.Paragraph("${'$'}${'$'}\\frac{1}{2")),
            parseMarkdownBlocks("${'$'}${'$'}\\frac{1}{2"),
        )
    }

    @Test
    fun `recognizes numeric formulas but not plain dollar amounts`() {
        assertEquals(
            listOf(MathInlineSegment.Math("2 + 2", "${'$'}2 + 2${'$'}")),
            parseInlineMath("${'$'}2 + 2${'$'}"),
        )
        assertEquals(
            listOf(MathInlineSegment.Text("${'$'}20${'$'}")),
            parseInlineMath("${'$'}20${'$'}"),
        )
    }

    @Test
    fun `does not reuse the closing delimiter of rejected dollar math`() {
        val source =
            "Aproximadamente ${'$'}299.792.458 \\text{metros por segundo}(\\text{m/s}${'$'}). " +
                "Para a água, use ${'$'}1,33${'$'} e obtenha " +
                "${'$'}225.407.863 \\text{ m/s}${'$'}."

        assertEquals(
            listOf(
                MathInlineSegment.Text(
                    "Aproximadamente ${'$'}299.792.458 \\text{metros por segundo}(\\text{m/s}${'$'}). " +
                        "Para a água, use ${'$'}1,33${'$'} e obtenha ",
                ),
                MathInlineSegment.Math(
                    "225.407.863 \\text{ m/s}",
                    "${'$'}225.407.863 \\text{ m/s}${'$'}",
                ),
                MathInlineSegment.Text("."),
            ),
            parseInlineMath(source),
        )
    }

    @Test
    fun `renders numeric latex with text units and multiplication`() {
        val source =
            "Distance ${'$'}385,000 \\text{ km}${'$'} and speed " +
                "${'$'}2.25 \\times 10^8 \\text{ m/s}${'$'}."

        assertEquals(
            listOf(
                MathInlineSegment.Text("Distance "),
                MathInlineSegment.Math("385,000 \\text{ km}", "${'$'}385,000 \\text{ km}${'$'}"),
                MathInlineSegment.Text(" and speed "),
                MathInlineSegment.Math(
                    "2.25 \\times 10^8 \\text{ m/s}",
                    "${'$'}2.25 \\times 10^8 \\text{ m/s}${'$'}",
                ),
                MathInlineSegment.Text("."),
            ),
            parseInlineMath(source),
        )
    }
}
