package com.jesjobom.ararai.widget

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jesjobom.ararai.ui.ManagedWidgetDetailScreen
import com.jesjobom.ararai.ui.ManagedWidgetDetailUiState
import com.jesjobom.ararai.ui.ManagedWidgetsScreen
import com.jesjobom.ararai.widget.managed.ManagedWidgetDefinition
import com.jesjobom.ararai.widget.managed.ManagedWidgetStatus
import com.jesjobom.ararai.widget.managed.WidgetProgramRevision
import com.jesjobom.ararai.widget.runtime.StrictJson
import com.jesjobom.ararai.widget.runtime.WidgetPresentationNode
import com.jesjobom.ararai.widget.runtime.sha256
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ManagedWidgetManagementComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyManagerOffersPromptBasedCreation() {
        var creations = 0
        composeRule.setContent {
            MaterialTheme {
                ManagedWidgetsScreen(
                    items = emptyList(),
                    loadFailed = false,
                    onBack = {},
                    onCreate = { creations += 1 },
                    onOpenWidget = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithText("No widgets yet").assertIsDisplayed()
        composeRule.onAllNodesWithText("Create widget")[0].performClick()
        composeRule.runOnIdle { assertEquals(1, creations) }
    }

    @Test
    fun detailShowsPresentationPermissionsHistoryAndGuardedActions() {
        val actions = mutableListOf<String>()
        composeRule.setContent {
            MaterialTheme {
                ManagedWidgetDetailScreen(
                    state = detail(),
                    loadFailed = false,
                    busy = false,
                    onBack = { actions += "back" },
                    onEdit = { actions += "edit" },
                    onRefresh = { actions += "refresh" },
                    onEnabledChange = { actions += "enabled:$it" },
                    onDuplicate = { actions += "duplicate" },
                    onDelete = { actions += "delete" },
                    onRetry = { actions += "retry" },
                )
            }
        }

        composeRule.onNodeWithText("Ada Lovelace").assertIsDisplayed()
        composeRule.onNodeWithText("Tools: wikipedia_pages@1").assertIsDisplayed()
        composeRule.onNodeWithText("No execution history.").assertIsDisplayed()
        composeRule.onNodeWithText("Refresh now").performClick()
        composeRule.onNodeWithText("Edit").performClick()
        composeRule.onNodeWithText("Duplicate").performClick()
        composeRule.onNodeWithText("Disable").performClick()
        composeRule.runOnIdle {
            assertEquals(listOf("refresh", "edit", "duplicate", "enabled:false"), actions)
        }
    }

    private fun detail(): ManagedWidgetDetailUiState {
        val source = "function plan(){return []} function render(){return {type:'text',text:'x',tone:'neutral'}}"
        val sourceDigest = sha256(source)
        val manifest = StrictJson.canonical(
            com.google.gson.JsonParser.parseString(
                """{
                  "schemaVersion":1,
                  "language":"javascript",
                  "apiVersion":1,
                  "entrypoints":{"plan":"plan","render":"render"},
                  "capabilities":{
                    "tools":[{"id":"wikipedia_pages","version":1}],
                    "runtime":["local_time"],
                    "presentation":["text"]
                  },
                  "limits":{
                    "memoryBytes":1048576,"stackBytes":65536,"executionMillis":10,
                    "maxToolCalls":1,"maxOutputBytes":4096
                  },
                  "sourceSha256":"$sourceDigest"
                }
                """.trimIndent(),
            ),
        )
        return ManagedWidgetDetailUiState(
            definition = ManagedWidgetDefinition(
                id = "widget-1",
                displayName = "History",
                enabled = true,
                periodicIntervalHours = 24,
                activeRevision = 1,
                consentDigest = "a".repeat(64),
                status = ManagedWidgetStatus.Ready,
                createdAtMillis = 1,
                updatedAtMillis = 1,
            ),
            revision = WidgetProgramRevision("widget-1", 1, manifest, source, "b".repeat(64), 1),
            revisions = emptyList(),
            runs = emptyList(),
            presentation = WidgetPresentationNode.Text("Ada Lovelace", "neutral"),
            presentationCompletedAtMillis = 1,
            presentationInvalid = false,
        )
    }
}
