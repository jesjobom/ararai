package com.jesjobom.ararai.widget

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jesjobom.ararai.ui.ManagedWidgetDetailScreen
import com.jesjobom.ararai.ui.ManagedWidgetDetailUiState
import com.jesjobom.ararai.ui.ManagedWidgetListItemUiState
import com.jesjobom.ararai.ui.ManagedWidgetsScreen
import com.jesjobom.ararai.widget.managed.ManagedWidgetDefinition
import com.jesjobom.ararai.widget.managed.ManagedWidgetStatus
import com.jesjobom.ararai.widget.managed.WidgetProgramRevision
import com.jesjobom.ararai.widget.managed.WidgetRunOutcome
import com.jesjobom.ararai.widget.managed.WidgetRunRecord
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
                    onEnabledChange = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText("No widgets yet").assertIsDisplayed()
        composeRule.onAllNodesWithText("Create widget")[0].performClick()
        composeRule.runOnIdle { assertEquals(1, creations) }
    }

    @Test
    fun listSwitchChangesEnablementWithoutOpeningWidget() {
        var definition by mutableStateOf(detail().definition)
        val toggles = mutableListOf<Pair<String, Boolean>>()
        val opened = mutableListOf<String>()
        composeRule.setContent {
            MaterialTheme {
                ManagedWidgetsScreen(
                    items = listOf(ManagedWidgetListItemUiState(definition, null)),
                    loadFailed = false,
                    onBack = {},
                    onCreate = {},
                    onOpenWidget = { opened += it },
                    onRetry = {},
                    onEnabledChange = { id, enabled -> toggles += id to enabled },
                )
            }
        }

        composeRule.onNodeWithText("Enabled").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Disable History").assertIsOn().performClick()
        composeRule.runOnIdle {
            assertEquals(listOf("widget-1" to false), toggles)
            assertEquals(emptyList<String>(), opened)
            definition = definition.copy(enabled = false)
        }
        composeRule.onNodeWithContentDescription("Enable History").assertIsOff()
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
                    onEdit = { actions += "edit:$it" },
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
        composeRule.onNodeWithText("Frequency").assertExists()
        composeRule.onNodeWithText("Active revision 1").assertExists()
        composeRule.onNodeWithText("Revision").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("About background timing").assertExists()
        composeRule.onNodeWithText("Enabled").assertDoesNotExist()
        composeRule.onNodeWithText("Refresh now").performClick()
        composeRule.onNodeWithContentDescription("More widget actions").performClick()
        composeRule.onNodeWithText("Edit").performClick()
        composeRule.onNodeWithContentDescription("More widget actions").performClick()
        composeRule.onNodeWithText("Duplicate").performClick()
        composeRule.onNodeWithContentDescription("More widget actions").performClick()
        composeRule.onNodeWithText("Delete").performClick()
        composeRule.onNodeWithText("Delete this widget?").assertExists()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithContentDescription("Disable History").performClick()
        composeRule.runOnIdle {
            assertEquals(listOf("refresh", "edit:original prompt", "duplicate", "enabled:false"), actions)
        }
    }

    @Test
    fun detailShowsFourRecentRunsUntilExpanded() {
        val runs = (6 downTo 1).map { revision ->
            WidgetRunRecord(
                id = "run-$revision",
                widgetId = "widget-1",
                revision = revision,
                startedAtMillis = revision * 1000L,
                completedAtMillis = revision * 1000L + 1,
                durationMillis = 1,
                plannedToolIds = emptyList(),
                outcome = WidgetRunOutcome.Success,
                diagnostic = null,
            )
        }
        composeRule.setContent {
            MaterialTheme {
                ManagedWidgetDetailScreen(
                    state = detail().copy(runs = runs.reversed()),
                    loadFailed = false,
                    busy = false,
                    onBack = {},
                    onEdit = {},
                    onRefresh = {},
                    onEnabledChange = {},
                    onDuplicate = {},
                    onDelete = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithText("revision 6", substring = true).assertExists()
        composeRule.onNodeWithText("revision 2", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Show all 6 runs").performClick()
        composeRule.onNodeWithText("revision 2", substring = true).assertExists()
        composeRule.onNodeWithText("Show fewer runs").performClick()
        composeRule.onNodeWithText("revision 2", substring = true).assertDoesNotExist()
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
            revision = WidgetProgramRevision("widget-1", 1, manifest, source, "b".repeat(64), 1, "original prompt"),
            revisions = emptyList(),
            runs = emptyList(),
            presentation = WidgetPresentationNode.Text("Ada Lovelace", "neutral"),
            presentationCompletedAtMillis = 1,
            presentationInvalid = false,
        )
    }
}
