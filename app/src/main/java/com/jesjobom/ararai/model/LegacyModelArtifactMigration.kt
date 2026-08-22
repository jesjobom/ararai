package com.jesjobom.ararai.model

import java.io.File

object LegacyModelArtifactMigration {
    private val removedRelativePaths =
        listOf(
            "models/SmolLM2-135M-Instruct-Q4_K_M.gguf",
            "models/Llama-3.2-3B-Instruct-Q4_K_M.gguf",
            "models/LFM2.5-1.2B-Instruct-Q4_K_M.gguf",
            "models/Ministral-3-3B-Instruct-2512-Q4_K_M.gguf",
        )

    fun run(appFilesRoot: File) {
        removedRelativePaths.forEach { relativePath ->
            val managedFile = ModelPathPolicy.resolveContained(appFilesRoot, relativePath)
            ModelFileIntegrity.invalidate(managedFile)
            managedFile.delete()
            File(managedFile.parentFile, "${managedFile.name}.part").delete()
        }
    }
}
