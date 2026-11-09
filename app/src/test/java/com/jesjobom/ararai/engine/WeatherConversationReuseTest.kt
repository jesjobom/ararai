package com.jesjobom.ararai.engine

import com.jesjobom.ararai.tools.WEATHER_FORECAST_TOOL_NAME
import org.junit.Assert.assertFalse
import org.junit.Test

class WeatherConversationReuseTest {
    @Test
    fun `weather-enabled conversation is not reused even with a matching transcript`() {
        val key = LiteRtLmConversationKey(
            sessionId = "session-1",
            temperature = 0.7f,
            topP = 0.9f,
            reasoningEnabled = false,
            advertisedToolNames = setOf(WEATHER_FORECAST_TOOL_NAME),
        )
        val transcript = listOf(
            PromptChatMessage(PromptChatRole.User, "question"),
            PromptChatMessage(PromptChatRole.Assistant, "answer"),
        )

        assertFalse(canReuseLiteRtLmConversation(key, transcript, key, transcript))
    }
}
