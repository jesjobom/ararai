package com.jesjobom.ararai.widget

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.jesjobom.ararai.ui.ManagedWidgetsScreen
import com.jesjobom.ararai.ui.WidgetToolsScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WidgetToolNavigationComposeTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun widgetListKeepsCreationAndOpensToolsFromTopBar() {
        val actions = mutableListOf<String>()
        composeRule.setContent {
            MaterialTheme {
                ManagedWidgetsScreen(
                    items = emptyList(),
                    loadFailed = false,
                    onBack = {},
                    onCreate = { actions += "create" },
                    onOpenTools = { actions += "tools" },
                    onOpenWidget = {},
                    onRetry = {},
                    onEnabledChange = { _, _ -> },
                )
            }
        }
        composeRule.onNodeWithTag("widgets-open-tools").performClick()
        composeRule.runOnIdle { assertEquals(listOf("tools"), actions) }
    }

    @Test
    fun widgetToolToggleUsesItsOwnState() {
        var enabled by mutableStateOf(false)
        composeRule.setContent {
            MaterialTheme {
                WidgetToolsScreen(
                    contracts = emptyList(),
                    wikipediaEnabled = enabled,
                    onWikipediaEnabledChange = { enabled = it },
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithTag("widget-wikipedia-enabled").assertIsOff().performClick().assertIsOn()
    }
}
