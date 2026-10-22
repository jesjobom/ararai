package com.jesjobom.ararai.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString

private val JAVA_SCRIPT_KEYWORDS = setOf(
    "async", "await", "break", "case", "catch", "class", "const", "continue", "debugger", "default",
    "delete", "do", "else", "export", "extends", "false", "finally", "for", "function", "if",
    "import", "in", "instanceof", "let", "new", "null", "of", "return", "static", "super",
    "switch", "this", "throw", "true", "try", "typeof", "undefined", "var", "void", "while", "yield",
)

internal fun highlightJavaScript(
    source: String,
    keywordColor: Color,
    stringColor: Color,
    numberColor: Color,
    commentColor: Color,
): AnnotatedString = buildAnnotatedString {
    append(source)
    var cursor = 0
    while (cursor < source.length) {
        val start = cursor
        when {
            source.startsWith("//", cursor) -> {
                cursor = source.lineCommentEnd(cursor)
                addStyle(SpanStyle(color = commentColor), start, cursor)
            }
            source.startsWith("/*", cursor) -> {
                cursor = source.blockCommentEnd(cursor)
                addStyle(SpanStyle(color = commentColor), start, cursor)
            }
            source[cursor].isQuote() -> {
                cursor = source.quotedEnd(cursor)
                addStyle(SpanStyle(color = stringColor), start, cursor)
            }
            source[cursor].isDigit() -> {
                cursor = source.tokenEnd(cursor, Char::isNumberPart)
                addStyle(SpanStyle(color = numberColor), start, cursor)
            }
            source[cursor].isIdentifierStart() -> {
                cursor = source.tokenEnd(cursor, Char::isIdentifierPart)
                if (source.substring(start, cursor) in JAVA_SCRIPT_KEYWORDS) {
                    addStyle(SpanStyle(color = keywordColor), start, cursor)
                }
            }
            else -> cursor++
        }
    }
}

private fun String.lineCommentEnd(start: Int): Int = indexOf('\n', start).let { if (it == -1) length else it }

private fun String.blockCommentEnd(start: Int): Int = indexOf("*/", start + 2).let { if (it == -1) length else it + 2 }

private fun String.quotedEnd(start: Int): Int {
    val quote = this[start]
    var cursor = start + 1
    while (cursor < length) {
        when (this[cursor++]) {
            '\\' -> if (cursor < length) cursor++
            quote -> break
        }
    }
    return cursor
}

private fun String.tokenEnd(start: Int, isPart: (Char) -> Boolean): Int {
    var cursor = start + 1
    while (cursor < length && isPart(this[cursor])) cursor++
    return cursor
}

private fun Char.isQuote(): Boolean = this == '\'' || this == '"' || this == '`'

private fun Char.isNumberPart(): Boolean = isLetterOrDigit() || this == '_' || this == '.'

private fun Char.isIdentifierStart(): Boolean = isLetter() || this == '_' || this == '$'

private fun Char.isIdentifierPart(): Boolean = isLetterOrDigit() || this == '_' || this == '$'
