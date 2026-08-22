package com.jesjobom.ararai.chat

import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.LocalModel
import com.jesjobom.ararai.model.ManagedModelItem
import com.jesjobom.ararai.model.ModelAccelerationPolicy
import com.jesjobom.ararai.model.ModelArtifactFormat
import com.jesjobom.ararai.model.ModelConfig
import com.jesjobom.ararai.model.ModelPurpose
import com.jesjobom.ararai.model.ModelRuntime
import com.jesjobom.ararai.model.ModelStartupState
import com.jesjobom.ararai.model.ModelTask
import com.jesjobom.ararai.whisper.WhisperRuntimeResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class WhisperCppAudioTranscriberTest {
    @Test
    fun `uses first available transcription model and returns diagnostics`() = runTest {
        val audio = Files.createTempFile("ararai-whisper", ".wav").toFile()
        val calls = mutableListOf<List<Any>>()
        val transcriber = WhisperCppAudioTranscriber(
            models = { listOf(availableWhisper("base", "/models/base.bin")) },
            transcribeWithRuntime = { model, wav, language, threads ->
                calls += listOf(model, wav, language, threads)
                WhisperRuntimeResult("  Olá   mundo. ", 10, 20, 1000, threads)
            },
        )

        val result = transcriber.transcribe(AudioPrompt(audio.toURI().toString(), "audio/wav"))

        assertTrue(transcriber.isAvailable)
        assertEquals("Olá mundo.", result.transcript)
        assertEquals(listOf("/models/base.bin", audio.absolutePath, "auto", 6), calls.single())
        assertTrue(result.diagnosticReport.contains("model_id=base"))
        assertTrue(result.diagnosticReport.contains("language=auto"))
    }

    @Test
    fun `is unavailable when no downloaded transcription model exists`() {
        val transcriber = WhisperCppAudioTranscriber(
            models = { listOf(missingWhisper()) },
            transcribeWithRuntime = { _, _, _, _ -> error("must not run") },
        )

        assertFalse(transcriber.isAvailable)
        assertThrows(AudioTranscriptionException::class.java) {
            runTest {
                transcriber.transcribe(AudioPrompt("file:/missing.wav", "audio/wav"))
            }
        }
    }

    @Test
    fun `normalizes the configured application locale for Whisper`() = runTest {
        val audio = Files.createTempFile("ararai-whisper-language", ".wav").toFile()
        var requestedLanguage = ""
        val transcriber = WhisperCppAudioTranscriber(
            models = { listOf(availableWhisper("base", "/models/base.bin")) },
            languageTag = { "pt-BR" },
            transcribeWithRuntime = { _, _, language, threads ->
                requestedLanguage = language
                WhisperRuntimeResult("Teste um dois.", 10, 20, 1000, threads)
            },
        )

        val result = transcriber.transcribe(AudioPrompt(audio.toURI().toString(), "audio/wav"))

        assertEquals("pt", requestedLanguage)
        assertEquals("Teste um dois.", result.transcript)
        assertTrue(result.diagnosticReport.contains("language=pt"))
    }

    private fun availableWhisper(id: String, path: String): ManagedModelItem {
        val config = whisperConfig(id)
        return ManagedModelItem(
            config,
            ModelStartupState.Available(
                LocalModel(
                    id = id,
                    name = id,
                    filePath = path,
                    runtime = ModelRuntime.WhisperCpp,
                    artifactFormat = ModelArtifactFormat.WhisperGgml,
                    acceleration = ModelAccelerationPolicy.CpuOnly,
                ),
                inference = null,
            ),
        )
    }

    private fun missingWhisper(): ManagedModelItem = ManagedModelItem(whisperConfig("base"), ModelStartupState.Missing)

    private fun whisperConfig(id: String): ModelConfig = ModelConfig(
        id = id,
        name = id,
        runtime = ModelRuntime.WhisperCpp,
        artifactFormat = ModelArtifactFormat.WhisperGgml,
        acceleration = ModelAccelerationPolicy.CpuOnly,
        url = "https://example.com/$id.bin",
        fileName = "$id.bin",
        relativePath = "models/utility/whisper/$id.bin",
        sha256 = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        expectedBytes = 1,
        purposes = setOf(ModelPurpose.Utility),
        tasks = setOf(ModelTask.Transcription),
        inference = null,
    )
}
