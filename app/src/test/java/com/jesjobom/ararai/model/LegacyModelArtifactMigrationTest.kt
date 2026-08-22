package com.jesjobom.ararai.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

class LegacyModelArtifactMigrationTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `removes exact legacy artifacts and partial downloads`() {
        val root = temporaryFolder.root
        val legacy = File(root, "models/SmolLM2-135M-Instruct-Q4_K_M.gguf").create()
        val partial = File(root, "models/Llama-3.2-3B-Instruct-Q4_K_M.gguf.part").create()

        LegacyModelArtifactMigration.run(root)

        assertFalse(legacy.exists())
        assertFalse(partial.exists())
    }

    @Test
    fun `preserves current and unknown model artifacts`() {
        val root = temporaryFolder.root
        val gemma = File(root, "models/gemma-4-E2B-it.litertlm").create()
        val whisper = File(root, "models/utility/whisper/ggml-base-q5_1.bin").create()
        val unknown = File(root, "models/imported.gguf").create()

        LegacyModelArtifactMigration.run(root)

        assertTrue(gemma.exists())
        assertTrue(whisper.exists())
        assertTrue(unknown.exists())
    }

    @Test
    fun `migration is idempotent`() {
        val root = temporaryFolder.root
        LegacyModelArtifactMigration.run(root)
        LegacyModelArtifactMigration.run(root)
    }

    @Test
    fun `does not delete legacy names through an escaped models symlink`() {
        val root = temporaryFolder.newFolder("app-files")
        val outside = temporaryFolder.newFolder("outside-models")
        val outsideLegacy = File(outside, "SmolLM2-135M-Instruct-Q4_K_M.gguf").create()
        Files.createSymbolicLink(root.toPath().resolve("models"), outside.toPath())

        assertThrows(IllegalArgumentException::class.java) {
            LegacyModelArtifactMigration.run(root)
        }

        assertTrue(outsideLegacy.exists())
    }

    private fun File.create(): File = apply {
        parentFile?.mkdirs()
        writeText("test")
    }
}
