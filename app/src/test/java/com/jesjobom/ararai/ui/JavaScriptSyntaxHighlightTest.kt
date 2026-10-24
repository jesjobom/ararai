package com.jesjobom.ararai.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class JavaScriptSyntaxHighlightTest {
    @Test
    fun commentsAndStringsAreNotHighlightedAsKeywords() {
        val source = "const text = \"return // literal\"; // function"

        val result = highlightJavaScript(source, Color.Red, Color.Green, Color.Blue, Color.Gray)

        assertEquals(source, result.text)
        assertEquals(
            listOf(
                Triple(Color.Red, 0, 5),
                Triple(Color.Green, 13, 32),
                Triple(Color.Gray, 34, source.length),
            ),
            result.spanStyles.map { Triple(it.item.color, it.start, it.end) },
        )
    }
}
