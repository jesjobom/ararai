package com.jesjobom.ararai

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.jesjobom.ararai.chat.ChatMessage
import com.jesjobom.ararai.chat.ChatRole
import com.jesjobom.ararai.ui.CompletedAssistantPreparationEffect
import com.jesjobom.ararai.ui.FollowLatestMessagesEffect
import com.jesjobom.ararai.ui.MarkdownBlock
import com.jesjobom.ararai.ui.MarkdownText
import com.jesjobom.ararai.ui.parseMarkdownBlocks
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ChatStreamingPresentationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun unrelatedRecompositionDoesNotReparseUnchangedMarkdown() {
        val unrelatedState = mutableIntStateOf(0)
        var parseCalls = 0
        val parser: (String) -> List<MarkdownBlock> = { source ->
            parseCalls += 1
            parseMarkdownBlocks(source)
        }

        composeRule.setContent {
            unrelatedState.intValue
            MaterialTheme {
                MarkdownText(
                    text = "**stable** response",
                    parseBlocks = parser,
                )
            }
        }
        composeRule.runOnIdle { assertEquals(1, parseCalls) }

        composeRule.runOnIdle { unrelatedState.intValue += 1 }

        composeRule.runOnIdle { assertEquals(1, parseCalls) }
    }

    @Test
    fun followLatestReactsToDisplayRevisionInsteadOfGrowingText() {
        val unrelatedState = mutableIntStateOf(0)
        val displayRevision = mutableIntStateOf(0)
        var followCalls = 0

        composeRule.setContent {
            unrelatedState.intValue
            FollowLatestMessagesEffect(
                sessionId = "session",
                messageCount = 2,
                displayRevision = displayRevision.intValue.toLong(),
                enabled = true,
                onFollowLatest = { followCalls += 1 },
            )
        }
        composeRule.waitForIdle()
        assertEquals(1, followCalls)

        composeRule.runOnIdle { unrelatedState.intValue += 1 }
        composeRule.waitForIdle()
        assertEquals(1, followCalls)

        composeRule.runOnIdle { displayRevision.intValue += 1 }
        composeRule.waitForIdle()
        assertEquals(2, followCalls)
    }

    @Test
    fun ttsPreparationReactsOnlyToCompletedAssistantSignal() {
        val completedSignal = androidx.compose.runtime.mutableStateOf<String?>(null)
        val messages = listOf(ChatMessage(ChatRole.Assistant, "complete", id = "assistant"))
        val prepared = mutableListOf<Pair<String, String>>()

        composeRule.setContent {
            CompletedAssistantPreparationEffect(
                completedMessageId = completedSignal.value,
                messages = messages,
                onPrepare = { id, text -> prepared += id to text },
            )
        }
        composeRule.waitForIdle()
        assertEquals(emptyList<Pair<String, String>>(), prepared)

        composeRule.runOnIdle { completedSignal.value = "assistant" }
        composeRule.waitForIdle()
        assertEquals(listOf("assistant" to "complete"), prepared)
    }
}
