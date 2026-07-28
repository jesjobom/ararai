package com.jesjobom.ararai.knowledge

data class ToolRequest(
    val query: String,
    val language: String = "en",
)

data class KnowledgeSource(
    val provider: String,
    val title: String,
    val canonicalUrl: String,
    val language: String,
    val retrievedAtMillis: Long,
)

sealed interface ToolResult {
    data class Success(
        val untrustedContext: String,
        val sources: List<KnowledgeSource>,
    ) : ToolResult

    data class Failure(
        val reason: ToolFailureReason,
    ) : ToolResult
}

enum class ToolFailureReason {
    InvalidArguments,
    NoResults,
    Unavailable,
    TimedOut,
    Cancelled,
}

fun interface KnowledgeTool {
    suspend fun execute(request: ToolRequest): ToolResult
}
