package com.jesjobom.ararai.engine

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
    retainedKey == requestKey &&
    retainedTranscript == requestHistory
