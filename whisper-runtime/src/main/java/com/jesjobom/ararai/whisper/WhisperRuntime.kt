package com.jesjobom.ararai.whisper

object WhisperRuntime {
    init {
        System.loadLibrary("ararai_whisper")
    }

    external fun systemInfo(): String

    fun transcribe(
        modelPath: String,
        wavPath: String,
        language: String = "pt",
        threads: Int = 4,
    ): WhisperRuntimeResult {
        require(threads > 0) { "threads must be positive" }
        val values = nativeTranscribe(modelPath, wavPath, language, threads)
        check(values.size == RESULT_FIELD_COUNT) { "Unexpected native Whisper result" }
        return WhisperRuntimeResult(
            text = values[0],
            loadMillis = values[1].toLong(),
            transcriptionMillis = values[2].toLong(),
            audioMillis = values[3].toLong(),
            threads = values[4].toInt(),
        )
    }

    private external fun nativeTranscribe(
        modelPath: String,
        wavPath: String,
        language: String,
        threads: Int,
    ): Array<String>

    private const val RESULT_FIELD_COUNT = 5
}

data class WhisperRuntimeResult(
    val text: String,
    val loadMillis: Long,
    val transcriptionMillis: Long,
    val audioMillis: Long,
    val threads: Int,
) {
    val realTimeFactor: Double
        get() = if (audioMillis > 0L) transcriptionMillis.toDouble() / audioMillis else 0.0
}
