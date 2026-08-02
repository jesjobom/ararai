package com.jesjobom.ararai.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class KnowledgeToolStatusTextTest {
    @Test
    fun `uses the active tool name in research status`() {
        assertEquals("Using Wikipedia", knowledgeToolStatusText("Wikipedia"))
        assertEquals("Using Tavily", knowledgeToolStatusText("Tavily"))
        assertEquals("Using Exa", knowledgeToolStatusText("Exa"))
    }

    @Test
    fun `uses a generic status when tool name is unavailable`() {
        assertEquals("Using tool", knowledgeToolStatusText(null))
        assertEquals("Using tool", knowledgeToolStatusText(" "))
    }
}
