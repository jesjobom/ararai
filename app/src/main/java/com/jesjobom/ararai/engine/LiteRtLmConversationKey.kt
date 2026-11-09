package com.jesjobom.ararai.engine

import com.jesjobom.ararai.tools.WEATHER_FORECAST_TOOL_NAME

internal data class LiteRtLmConversationKey(
    val sessionId: String?,
    val temperature: Float,
    val topP: Float,
    val reasoningEnabled: Boolean,
    val systemInstruction: String? = null,
    val advertisedToolNames: Set<String> = emptySet(),
)

internal fun canReuseLiteRtLmConversation(
    retainedKey: LiteRtLmConversationKey,
    retainedTranscript: List<PromptChatMessage>,
    requestKey: LiteRtLmConversationKey,
    requestHistory: List<PromptChatMessage>,
): Boolean = requestKey.sessionId != null &&
    WEATHER_FORECAST_TOOL_NAME !in requestKey.advertisedToolNames &&
    retainedKey == requestKey &&
    retainedTranscript == requestHistory
