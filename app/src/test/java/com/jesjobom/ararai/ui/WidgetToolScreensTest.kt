package com.jesjobom.ararai.ui

import com.jesjobom.ararai.tools.ApplicationToolCategory
import com.jesjobom.ararai.tools.ApplicationToolConsumer
import com.jesjobom.ararai.tools.ApplicationToolContract
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetToolScreensTest {
    @Test
    fun `widget tool list shows both web tools but only newest Wikipedia pages`() {
        val pagesV1 = contract("wikipedia_pages", 1)
        val pagesV2 = contract("wikipedia_pages", 2)
        val events = contract("wikipedia_on_this_day", 1)
        val chatOnly = contract("web_search", 1, setOf(ApplicationToolConsumer.Model))
        val widgetWebSearch = contract("web_search_top_results", 1)
        val widgetWebContent = contract("web_search_content", 1)

        assertEquals(
            listOf(pagesV2, events, widgetWebSearch, widgetWebContent),
            listOf(pagesV1, pagesV2, events, chatOnly, widgetWebSearch, widgetWebContent)
                .currentWidgetToolContracts(),
        )
    }

    private fun contract(
        id: String,
        version: Int,
        consumers: Set<ApplicationToolConsumer> = setOf(ApplicationToolConsumer.Widget),
    ) = ApplicationToolContract(
        id = id,
        version = version,
        displayName = id,
        category = ApplicationToolCategory.ExternalKnowledge,
        consumers = consumers,
        inputSchemaJson = "{}",
        outputSchemaJson = "{}",
    )
}
