package com.jesjobom.ararai.ui

import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.ManagedModelItem
import com.jesjobom.ararai.model.ModelConfig
import com.jesjobom.ararai.model.ModelPurpose
import com.jesjobom.ararai.model.ModelStartupState
import com.jesjobom.ararai.model.ModelTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogPresentationTest {
    @Test
    fun `reasoning models preserve families while ordering light to heavy`() {
        val models = listOf(
            item("gemma-large", "gemma", 400),
            item("other", "other", 200),
            item("gemma-small", "gemma", 100),
            item("tiny", "tiny", 50),
        )

        assertEquals(
            listOf("tiny", "gemma-small", "gemma-large", "other"),
            models.forTab(ModelCatalogTab.Chat).map { it.config.id },
        )
    }

    @Test
    fun `tabs partition llm and transcription models`() {
        val llm = item("llm", "llm", 100)
        val whisper = item(
            id = "whisper",
            family = "whisper",
            bytes = 50,
            purposes = setOf(ModelPurpose.Utility),
            tasks = setOf(ModelTask.Transcription),
        )

        assertEquals(listOf(llm), listOf(whisper, llm).forTab(ModelCatalogTab.Chat))
        assertEquals(listOf(whisper), listOf(whisper, llm).forTab(ModelCatalogTab.Transcription))
    }

    @Test
    fun `recommendation requires declared ram to fit available memory`() {
        val fitting = item("fit", "fit", 100, recommendedRam = 1_000)
        val heavy = item("heavy", "heavy", 200, recommendedRam = 2_000)

        assertTrue(fitting.isRecommendedFor(1_500))
        assertFalse(heavy.isRecommendedFor(1_500))
        assertFalse(fitting.isRecommendedFor(null))
    }

    @Suppress("LongParameterList")
    private fun item(
        id: String,
        family: String,
        bytes: Long,
        recommendedRam: Long? = null,
        purposes: Set<ModelPurpose> = setOf(ModelPurpose.Chat),
        tasks: Set<ModelTask> = setOf(ModelTask.Chat),
    ) = ManagedModelItem(
        config = ModelConfig(
            id = id,
            name = id,
            family = family,
            url = "https://example.com/$id.gguf",
            fileName = "$id.gguf",
            relativePath = "models/$id.gguf",
            sha256 = "a".repeat(64),
            expectedBytes = bytes,
            recommendedFreeRamBytes = recommendedRam,
            purposes = purposes,
            tasks = tasks,
            inference = if (ModelPurpose.Chat in purposes) {
                InferenceConfig(1024, 128, 0.7f, 0.9f)
            } else {
                null
            },
        ),
        state = ModelStartupState.Missing,
    )
}
