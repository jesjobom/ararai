package com.jesjobom.ararai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.ImageView
import ru.noties.jlatexmath.JLatexMathDrawable

internal sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data class ListBlock(val ordered: Boolean, val items: List<String>) : MarkdownBlock
    data class Quote(val text: String) : MarkdownBlock
    data class Code(val text: String) : MarkdownBlock
    data class Math(val latex: String, val source: String) : MarkdownBlock
    data object Rule : MarkdownBlock
}

internal sealed interface MathInlineSegment {
    data class Text(val text: String) : MathInlineSegment
    data class Math(val latex: String, val source: String) : MathInlineSegment
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

        displayMath(lines, index)?.let { parsed ->
            blocks += parsed.block
            index = parsed.nextIndex
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
                is MarkdownBlock.Heading -> InlineMarkdownText(
                    text = block.text,
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.headlineSmall
                        2 -> MaterialTheme.typography.titleLarge
                        else -> MaterialTheme.typography.titleMedium
                    },
                    fontWeight = FontWeight.Bold,
                )
                is MarkdownBlock.Paragraph -> InlineMarkdownText(
                    text = block.text,
                    style = MaterialTheme.typography.bodyLarge,
                )
                is MarkdownBlock.ListBlock -> Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    block.items.forEachIndexed { itemIndex, item ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (block.ordered) "${itemIndex + 1}." else "•")
                            InlineMarkdownText(
                                text = item,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                is MarkdownBlock.Quote -> InlineMarkdownText(
                    text = block.text,
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
                is MarkdownBlock.Math -> MathFormula(
                    latex = block.latex,
                    source = block.source,
                    display = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                MarkdownBlock.Rule -> HorizontalDivider()
            }
        }
    }
}

@Composable
private fun InlineMarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
) {
    val segments = parseInlineMath(text)
    if (segments.size == 1 && segments.single() is MathInlineSegment.Text) {
        Text(
            text = markdownInline((segments.single() as MathInlineSegment.Text).text),
            style = style,
            fontStyle = fontStyle,
            fontWeight = fontWeight,
            modifier = modifier,
        )
        return
    }
    FlowRow(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
    ) {
        segments.forEach { segment ->
            when (segment) {
                is MathInlineSegment.Text -> Text(
                    text = markdownInline(segment.text),
                    style = style,
                    fontStyle = fontStyle,
                    fontWeight = fontWeight,
                )
                is MathInlineSegment.Math -> MathFormula(
                    latex = segment.latex,
                    source = segment.source,
                    display = false,
                )
            }
        }
    }
}

@Composable
private fun MathFormula(
    latex: String,
    source: String,
    display: Boolean,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val color = LocalContentColor.current
    val textSizePx = with(density) { (if (display) 20.sp else 18.sp).toPx() }
    val drawable = remember(latex, color, textSizePx, display) {
        runCatching {
            JLatexMathDrawable.builder(latex)
                .textSize(textSizePx)
                .color(color.toArgb())
                .align(if (display) JLatexMathDrawable.ALIGN_CENTER else JLatexMathDrawable.ALIGN_LEFT)
                .padding(if (display) 4 else 1)
                .build()
        }.getOrNull()
    }
    if (drawable == null) {
        Text(source, style = MaterialTheme.typography.bodyLarge, modifier = modifier)
        return
    }

    val intrinsicWidth = with(density) { drawable.intrinsicWidth.toDp() }
    val intrinsicHeight = with(density) { drawable.intrinsicHeight.toDp() }
    AndroidView(
        factory = { context ->
            ImageView(context).apply {
                adjustViewBounds = true
                scaleType = if (display) ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.FIT_START
            }
        },
        update = { it.setImageDrawable(drawable) },
        modifier = modifier
            .then(if (display) Modifier else Modifier.width(intrinsicWidth.coerceAtMost(320.dp)))
            .height(intrinsicHeight.coerceAtLeast(20.dp))
            .widthIn(min = if (display) 1.dp else intrinsicWidth.coerceAtMost(320.dp)),
    )
}

private data class ParsedDisplayMath(val block: MarkdownBlock.Math, val nextIndex: Int)

private fun displayMath(lines: List<String>, start: Int): ParsedDisplayMath? {
    val trimmed = lines[start].trim()
    val (opening, closing) = when {
        trimmed.startsWith("\$\$") -> "\$\$" to "\$\$"
        trimmed.startsWith("\\[") -> "\\[" to "\\]"
        else -> return null
    }
    val afterOpening = trimmed.removePrefix(opening)
    if (afterOpening.endsWith(closing) && afterOpening.length >= closing.length) {
        val latex = afterOpening.removeSuffix(closing).trim()
        if (latex.isNotEmpty()) return ParsedDisplayMath(MarkdownBlock.Math(latex, trimmed), start + 1)
        return null
    }
    if (trimmed != opening) return null

    val content = mutableListOf<String>()
    var index = start + 1
    while (index < lines.size && lines[index].trim() != closing) {
        content += lines[index]
        index += 1
    }
    if (index >= lines.size) return null
    val latex = content.joinToString("\n").trim()
    if (latex.isEmpty()) return null
    val source = (listOf(opening) + content + closing).joinToString("\n")
    return ParsedDisplayMath(MarkdownBlock.Math(latex, source), index + 1)
}

internal fun parseInlineMath(text: String): List<MathInlineSegment> {
    val result = mutableListOf<MathInlineSegment>()
    var textStart = 0
    var cursor = 0
    while (cursor < text.length) {
        val dollar = text[cursor] == '$' && !isEscaped(text, cursor) && text.getOrNull(cursor + 1) != '$'
        val parenthesized = text.startsWith("\\(", cursor) && !isEscaped(text, cursor)
        if (!dollar && !parenthesized) {
            cursor += 1
            continue
        }
        val openingLength = if (dollar) 1 else 2
        val closing = if (dollar) "$" else "\\)"
        val close = findClosingDelimiter(text, cursor + openingLength, closing)
        if (close < 0) {
            cursor += openingLength
            continue
        }
        val latex = text.substring(cursor + openingLength, close)
        if (latex.isBlank() || (dollar && !isDollarMath(latex))) {
            cursor += openingLength
            continue
        }
        if (cursor > textStart) result += MathInlineSegment.Text(text.substring(textStart, cursor))
        val end = close + closing.length
        result += MathInlineSegment.Math(latex.trim(), text.substring(cursor, end))
        cursor = end
        textStart = end
    }
    if (textStart < text.length) result += MathInlineSegment.Text(text.substring(textStart))
    return result.ifEmpty { listOf(MathInlineSegment.Text(text)) }
}

private fun findClosingDelimiter(text: String, start: Int, delimiter: String): Int {
    var index = start
    while (index <= text.length - delimiter.length) {
        if (text.startsWith(delimiter, index) && !isEscaped(text, index)) {
            if (delimiter != "$" || text.getOrNull(index + 1) != '$') return index
        }
        index += 1
    }
    return -1
}

private fun isEscaped(text: String, index: Int): Boolean {
    var slashes = 0
    var cursor = index - 1
    while (cursor >= 0 && text[cursor] == '\\') {
        slashes += 1
        cursor -= 1
    }
    return slashes % 2 == 1
}

private fun isDollarMath(latex: String): Boolean {
    val trimmed = latex.trim()
    if (trimmed.firstOrNull()?.isDigit() != true) return true
    val withoutCommands = trimmed.replace(Regex("\\\\[A-Za-z]+"), "")
    if (withoutCommands.any(Char::isLetter) || ';' in withoutCommands) return false
    return trimmed.any { it in "\\^_=+*/{}()-" }
}

@Composable
private fun markdownInline(text: String): AnnotatedString = parseInlineMarkdown(
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
        trimmed.startsWith("\$\$") ||
        trimmed.startsWith("\\[") ||
        HEADING.matches(trimmed) ||
        HORIZONTAL_RULE.matches(trimmed) ||
        trimmed.startsWith(">") ||
        listItem(trimmed) != null
}
