package com.jesjobom.ararai.widget

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jesjobom.ararai.ui.ManagedWidgetPresentation
import com.jesjobom.ararai.widget.runtime.WidgetPresentationNode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ManagedWidgetPresentationComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersFixedNodesAndOpensAllowedLinkOnlyAfterClick() {
        var openedUrl: String? = null
        composeRule.setContent {
            MaterialTheme {
                ManagedWidgetPresentation(
                    presentation = WidgetPresentationNode.Card(
                        WidgetPresentationNode.Column(
                            listOf(
                                WidgetPresentationNode.Icon("info"),
                                WidgetPresentationNode.Text("Ada Lovelace", "neutral"),
                                WidgetPresentationNode.Value("1815", "muted"),
                                wikipediaLink("https://en.wikipedia.org/wiki/Ada_Lovelace"),
                            ),
                        ),
                    ),
                    onOpenWikipediaArticle = { openedUrl = it },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Information").assertIsDisplayed()
        composeRule.onNodeWithText("Ada Lovelace").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(null, openedUrl) }
        composeRule.onNodeWithText("Read article").performClick()
        composeRule.runOnIdle {
            assertEquals("https://en.wikipedia.org/wiki/Ada_Lovelace", openedUrl)
        }
    }

    @Test
    fun omitsUnsafeLinkAction() {
        composeRule.setContent {
            MaterialTheme {
                ManagedWidgetPresentation(
                    presentation = wikipediaLink("https://evil.test/wiki/Ada_Lovelace"),
                    onOpenWikipediaArticle = { error("Unsafe link callback must stay inert") },
                )
            }
        }

        composeRule.onNodeWithText("Read article").assertDoesNotExist()
    }

    private fun wikipediaLink(url: String) = WidgetPresentationNode.HttpsLink(
        label = "Read article",
        url = url,
        sourceAlias = "article",
        sourceField = "canonicalUrl",
    )
}
