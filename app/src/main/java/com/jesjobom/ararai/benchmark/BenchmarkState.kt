package com.jesjobom.ararai.benchmark

data class BenchmarkUiState(
    val modelName: String,
    val backendLabel: String,
    val promptLabel: String,
    val contextTokens: Int,
    val maxTokens: Int,
    val canRun: Boolean,
    val isRunning: Boolean = false,
    val status: String =
        if (canRun) {
            "Ready"
        } else {
            "Selected model must be available locally"
        },
    val result: BenchmarkResult? = null,
    val toolCallingSupported: Boolean = false,
    val characterizationCaseId: String = "multi-turn-reuse",
    val characterizationProgress: String? = null,
    val characterizationReport: ToolCallingCharacterizationReport? = null,
    val characterizationDiagnostic: String? = null,
    val error: String? = null,
)

data class BenchmarkResult(
    val loadMillis: Long,
    val firstTokenMillis: Long?,
    val generationMillis: Long,
    val totalMillis: Long,
    val prefillTokens: Int?,
    val prefillTokensPerSecond: Double?,
    val decodeTokens: Int?,
    val decodeTokensPerSecond: Double?,
    val generatedCharacters: Int,
    val streamedChunks: Int,
    val charactersPerSecond: Double,
)
