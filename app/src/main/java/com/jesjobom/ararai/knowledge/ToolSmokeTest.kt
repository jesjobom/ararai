package com.jesjobom.ararai.knowledge

data class ToolSmokeTestResult(
    val toolName: String,
    val passed: Boolean,
    val detail: String,
    val sources: List<KnowledgeSource> = emptyList(),
)

fun interface ToolSmokeTest {
    suspend fun run(): ToolSmokeTestResult
}

class WikipediaSmokeTest(
    private val tool: KnowledgeTool = WikipediaKnowledgeTool(),
) : ToolSmokeTest {
    override suspend fun run(): ToolSmokeTestResult = when (
        val result = tool.execute(ToolRequest(query = "Alan Turing", language = "en"))
    ) {
        is ToolResult.Success ->
            ToolSmokeTestResult(
                toolName = "Wikipedia",
                passed = result.sources.isNotEmpty(),
                detail = "Validated ${result.sources.size} source(s) from English Wikipedia.",
                sources = result.sources,
            )
        is ToolResult.Failure ->
            ToolSmokeTestResult(
                toolName = "Wikipedia",
                passed = false,
                detail = "Controlled failure: ${result.reason}",
            )
    }
}
