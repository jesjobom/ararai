package com.jesjobom.ararai.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jesjobom.ararai.R
import com.jesjobom.ararai.chat.AssistantCompletionStatus
import com.jesjobom.ararai.chat.AssistantIncompleteReason
import com.jesjobom.ararai.chat.MessageContent

@Composable
internal fun AssistantCompletionNotice(content: MessageContent.TextPrompt, isStreaming: Boolean) {
    if (content.completionStatus == AssistantCompletionStatus.Complete || isStreaming) return
    val capacityWarning = content.incompleteReason == AssistantIncompleteReason.ContextCapacityLikely
    val darkTheme = LocalArarAiDarkTheme.current
    Surface(
        color = if (capacityWarning) {
            if (darkTheme) Color(0xFF5F4500) else Color(0xFFFFF3CD)
        } else {
            MaterialTheme.colorScheme.errorContainer
        },
        contentColor = if (capacityWarning) {
            if (darkTheme) Color(0xFFFFE08A) else Color(0xFF4A3500)
        } else {
            MaterialTheme.colorScheme.onErrorContainer
        },
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(
                stringResource(
                    when (content.completionStatus) {
                        AssistantCompletionStatus.Cancelled -> R.string.chat_response_cancelled
                        AssistantCompletionStatus.PossiblyIncomplete -> R.string.chat_possible_incomplete_response
                        else -> R.string.chat_incomplete_response
                    },
                ),
                fontWeight = FontWeight.SemiBold,
            )
            if (content.completionStatus == AssistantCompletionStatus.Incomplete ||
                content.completionStatus == AssistantCompletionStatus.PossiblyIncomplete
            ) {
                Text(
                    stringResource(
                        if (content.completionStatus == AssistantCompletionStatus.PossiblyIncomplete) {
                            R.string.chat_context_capacity_output_warning
                        } else {
                            content.incompleteMessageResId()
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private fun MessageContent.TextPrompt.incompleteMessageResId(): Int = when {
    incompleteReason == AssistantIncompleteReason.ContextCapacityLikely -> R.string.chat_context_capacity_likely
    incompleteReason == AssistantIncompleteReason.AfterToolCall || sources.isNotEmpty() ->
        R.string.chat_no_final_after_tool
    else -> R.string.chat_no_final_answer
}
