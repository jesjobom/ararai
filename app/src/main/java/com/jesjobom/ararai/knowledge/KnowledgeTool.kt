package com.jesjobom.ararai.knowledge

data class ToolRequest(
    val query: String,
    val language: String = "en",
    val focus: String = query,
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
    AuthenticationFailed,
    QuotaExceeded,
    RateLimited,
    Unavailable,
    MalformedResponse,
    TimedOut,
    Cancelled,
}

fun interface KnowledgeTool {
    val displayName: String
        get() = "Tool"

    suspend fun execute(request: ToolRequest): ToolResult
}

class FallbackKnowledgeTool(
    private val tools: List<KnowledgeTool>,
) : KnowledgeTool {
    init {
        require(tools.isNotEmpty()) { "At least one knowledge tool is required" }
    }

    override val displayName: String =
        tools.joinToString(separator = " → ") { it.displayName }

    @Suppress("ReturnCount")
    override suspend fun execute(request: ToolRequest): ToolResult {
        var lastFailure: ToolResult.Failure? = null
        for (tool in tools) {
            when (val result = tool.execute(request)) {
                is ToolResult.Success -> return result
                is ToolResult.Failure -> {
                    lastFailure = result
                    if (!result.reason.allowsProviderFallback()) return result
                }
            }
        }
        return checkNotNull(lastFailure)
    }
}

private fun ToolFailureReason.allowsProviderFallback(): Boolean = when (this) {
    ToolFailureReason.InvalidArguments,
    ToolFailureReason.Cancelled,
    -> false
    ToolFailureReason.NoResults,
    ToolFailureReason.AuthenticationFailed,
    ToolFailureReason.QuotaExceeded,
    ToolFailureReason.RateLimited,
    ToolFailureReason.Unavailable,
    ToolFailureReason.MalformedResponse,
    ToolFailureReason.TimedOut,
    -> true
}
