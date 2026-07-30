package com.jesjobom.ararai.chat

import com.jesjobom.ararai.engine.PromptChatMessage
import com.jesjobom.ararai.engine.PromptChatRole
import com.jesjobom.ararai.model.InferenceConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationContextProjectorTest {
    private val inference =
        InferenceConfig(
            contextTokens = 256,
            promptReserveTokens = 32,
            temperature = 0.7f,
            topP = 0.9f,
        )

    @Test
    fun `projects completed audio transcripts from either chat mode`() {
        val history =
            listOf(
                stored(
                    role = ChatRole.User,
                    content =
                    MessageContent.AudioPromptContent(
                        audio = AudioPrompt("/persisted.wav", "audio/wav"),
                        transcript = "Pergunta por voz",
                        transcriptionStatus = AudioTranscriptionStatus.Completed,
                    ),
                ),
                stored(ChatRole.Assistant, MessageContent.TextPrompt("Resposta anterior")),
            )

        val projected =
            ConversationContextProjector("Be useful.").project(
                history = history,
                current = MessageContent.TextPrompt("Continue"),
                inferenceConfig = inference,
            )

        assertEquals(
            listOf(
                PromptChatMessage(PromptChatRole.System, "Be useful."),
                PromptChatMessage(PromptChatRole.User, "Pergunta por voz"),
                PromptChatMessage(PromptChatRole.Assistant, "Resposta anterior"),
                PromptChatMessage(PromptChatRole.User, "Continue"),
            ),
            projected.messages,
        )
    }

    @Test
    fun `omits pending audio instead of fabricating recovered context`() {
        val projected =
            ConversationContextProjector("Be useful.").project(
                history =
                listOf(
                    stored(
                        role = ChatRole.User,
                        content =
                        MessageContent.AudioPromptContent(
                            audio = AudioPrompt("/pending.wav", "audio/wav"),
                            transcriptionStatus = AudioTranscriptionStatus.Pending,
                        ),
                    ),
                ),
                current = MessageContent.TextPrompt("Continue"),
                inferenceConfig = inference,
            )

        assertFalse(projected.messages.any { it.text.contains("pending.wav") })
        assertTrue(projected.messages.contains(PromptChatMessage(PromptChatRole.User, "Continue")))
    }

    private fun stored(
        role: ChatRole,
        content: MessageContent,
    ) = StoredChatMessage(
        id = "${role.name}-${content.hashCode()}",
        sessionId = "shared",
        role = role,
        content = content,
        createdAtMillis = 1L,
    )
}
