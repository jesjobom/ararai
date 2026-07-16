package com.jesjobom.ararai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

internal sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data class ListBlock(val ordered: Boolean, val items: List<String>) : MarkdownBlock
    data class Quote(val text: String) : MarkdownBlock
    data class Code(val text: String) : MarkdownBlock
    data object Rule : MarkdownBlock
}

internal fun parseMarkdownBlocks(source: String): List<MarkdownBlock> {
    val lines = source.replace("\r\n", "\n").replace('\r', '\n').split('\n')
    val blocks = mutableListOf<MarkdownBlock>()
    var index = 0

    while (index < lines.size) {
        val line = lines[index]
        val trimmed = line.trim()
        if (trimmed.isEmpty()) {
            index += 1
            continue
        }

        if (trimmed.startsWith("```")) {
            index += 1
            val code = mutableListOf<String>()
            while (index < lines.size && !lines[index].trim().startsWith("```")) {
                code += lines[index]
                index += 1
            }
            if (index < lines.size) index += 1
            blocks += MarkdownBlock.Code(code.joinToString("\n"))
            continue
        }

        HEADING.matchEntire(trimmed)?.let { match ->
            blocks += MarkdownBlock.Heading(match.groupValues[1].length, match.groupValues[2])
            index += 1
            return@let
        }?.also { continue }

        if (HORIZONTAL_RULE.matches(trimmed)) {
            blocks += MarkdownBlock.Rule
            index += 1
            continue
        }

        if (trimmed.startsWith(">")) {
            val quote = mutableListOf<String>()
            while (index < lines.size && lines[index].trim().startsWith(">")) {
                quote += lines[index].trim().removePrefix(">").trimStart()
                index += 1
            }
            blocks += MarkdownBlock.Quote(quote.joinToString("\n"))
            continue
        }

        val firstListItem = listItem(trimmed)
        if (firstListItem != null) {
            val items = mutableListOf<String>()
            val ordered = firstListItem.first
            while (index < lines.size) {
                val item = listItem(lines[index].trim()) ?: break
                if (item.first != ordered) break
                items += item.second
                index += 1
            }
            blocks += MarkdownBlock.ListBlock(ordered = ordered, items = items)
            continue
        }

        val paragraph = mutableListOf<String>()
        while (index < lines.size && lines[index].isNotBlank() && !startsMarkdownBlock(lines[index], paragraph.isEmpty())) {
            paragraph += lines[index].trim()
            index += 1
        }
        if (paragraph.isEmpty()) {
            paragraph += line
            index += 1
        }
        blocks += MarkdownBlock.Paragraph(paragraph.joinToString("\n"))
    }

    return blocks
}

@Composable
internal fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
) {
    val blocks = parseMarkdownBlocks(text)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> Text(
                    text = markdownInline(block.text),
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.headlineSmall
                        2 -> MaterialTheme.typography.titleLarge
                        else -> MaterialTheme.typography.titleMedium
                    },
                    fontWeight = FontWeight.Bold,
                )
                is MarkdownBlock.Paragraph -> Text(
                    text = markdownInline(block.text),
                    style = MaterialTheme.typography.bodyLarge,
                )
                is MarkdownBlock.ListBlock -> Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    block.items.forEachIndexed { itemIndex, item ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (block.ordered) "${itemIndex + 1}." else "•")
                            Text(
                                text = markdownInline(item),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                is MarkdownBlock.Quote -> Text(
                    text = markdownInline(block.text),
                    style = MaterialTheme.typography.bodyLarge,
                    fontStyle = FontStyle.Italic,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
                is MarkdownBlock.Code -> Text(
                    text = block.text,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(8.dp),
                )
                MarkdownBlock.Rule -> HorizontalDivider()
            }
        }
    }
}

@Composable
private fun markdownInline(text: String): AnnotatedString =
    parseInlineMarkdown(
        text = text,
        linkColor = MaterialTheme.colorScheme.primary,
        codeBackground = MaterialTheme.colorScheme.surfaceVariant,
    )

internal fun parseInlineMarkdown(
    text: String,
    linkColor: Color = Color.Blue,
    codeBackground: Color = Color.LightGray,
): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    while (cursor < text.length) {
        val match = INLINE_PATTERNS
            .mapNotNull { pattern -> pattern.regex.find(text, cursor)?.let { pattern to it } }
            .minWithOrNull(compareBy<Pair<InlinePattern, MatchResult>> { it.second.range.first }.thenBy { it.first.priority })
        if (match == null) {
            append(text.substring(cursor))
            break
        }

        val (pattern, result) = match
        append(text.substring(cursor, result.range.first))
        val start = length
        append(result.groupValues[pattern.textGroup])
        val end = length
        when (pattern.kind) {
            InlineKind.Bold -> addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
            InlineKind.Italic -> addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
            InlineKind.Code -> addStyle(
                SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground),
                start,
                end,
            )
            InlineKind.Link -> {
                addStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline), start, end)
                addStringAnnotation("URL", result.groupValues[2], start, end)
            }
        }
        cursor = result.range.last + 1
    }
}

private data class InlinePattern(
    val kind: InlineKind,
    val regex: Regex,
    val textGroup: Int = 1,
    val priority: Int,
)

private enum class InlineKind { Bold, Italic, Code, Link }

private val INLINE_PATTERNS = listOf(
    InlinePattern(InlineKind.Code, Regex("`([^`\\n]+)`"), priority = 0),
    InlinePattern(InlineKind.Bold, Regex("\\*\\*([^*\\n]+)\\*\\*"), priority = 1),
    InlinePattern(InlineKind.Bold, Regex("__([^_\\n]+)__"), priority = 1),
    InlinePattern(InlineKind.Link, Regex("\\[([^]\\n]+)]\\(([^)\\s]+)\\)"), priority = 2),
    InlinePattern(InlineKind.Italic, Regex("\\*([^*\\n]+)\\*"), priority = 3),
    InlinePattern(InlineKind.Italic, Regex("_([^_\\n]+)_"), priority = 3),
)

private val HEADING = Regex("^(#{1,6})\\s+(.+)$")
private val HORIZONTAL_RULE = Regex("^(?:-{3,}|\\*{3,}|_{3,})$")
private val UNORDERED_LIST = Regex("^[-+*]\\s+(.+)$")
private val ORDERED_LIST = Regex("^\\d+[.)]\\s+(.+)$")

private fun listItem(line: String): Pair<Boolean, String>? =
    ORDERED_LIST.matchEntire(line)?.let { true to it.groupValues[1] }
        ?: UNORDERED_LIST.matchEntire(line)?.let { false to it.groupValues[1] }

private fun startsMarkdownBlock(line: String, allowParagraphStart: Boolean): Boolean {
    if (allowParagraphStart) return false
    val trimmed = line.trim()
    return trimmed.startsWith("```") ||
        HEADING.matches(trimmed) ||
        HORIZONTAL_RULE.matches(trimmed) ||
        trimmed.startsWith(">") ||
        listItem(trimmed) != null
}
