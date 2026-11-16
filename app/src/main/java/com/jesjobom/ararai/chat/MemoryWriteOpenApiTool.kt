package com.jesjobom.ararai.chat

import com.google.ai.edge.litertlm.OpenApiTool
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.jesjobom.ararai.knowledge.ApplicationToolExecutionEvent
import com.jesjobom.ararai.knowledge.ToolFailureReason
import com.jesjobom.ararai.tools.ApplicationToolConsumer
import com.jesjobom.ararai.tools.ApplicationToolDispatchResult
import com.jesjobom.ararai.tools.ApplicationToolDispatcher
import com.jesjobom.ararai.tools.ApplicationToolInvocation
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger

/** A turn-scoped model adapter; the registry gates tool availability and validates input bounds. */
class MemoryWriteOpenApiTool(
    private val dispatcher: ApplicationToolDispatcher,
    private val verifiedModelToolIds: Set<String>,
) : OpenApiTool {
    private val calls = AtomicInteger()
    private var observer: (ApplicationToolExecutionEvent) -> Unit = {}

    fun beginTurn(observer: (ApplicationToolExecutionEvent) -> Unit = {}) {
        calls.set(0)
        this.observer = observer
    }

    override fun getToolDescriptionJsonString(): String = DESCRIPTION

    override fun execute(paramsJsonString: String): String {
        if (calls.incrementAndGet() > 1) return failure("CALL_LIMIT_REACHED")
        observer(ApplicationToolExecutionEvent.Started)
        return try {
            when (
                val result = runBlocking {
                    dispatcher.execute(
                        ApplicationToolInvocation(
                            id = MEMORY_WRITE_TOOL_NAME,
                            version = 1,
                            consumer = ApplicationToolConsumer.Model,
                            argumentsJson = paramsJsonString,
                            verifiedModelToolIds = verifiedModelToolIds,
                        ),
                    )
                }
            ) {
                is ApplicationToolDispatchResult.Executed -> result.payloadJson.also { payload ->
                    val saved = runCatching { JsonParser.parseString(payload).asJsonObject["ok"].asBoolean }
                        .getOrDefault(false)
                    observer(
                        if (saved) {
                            ApplicationToolExecutionEvent.Succeeded(emptyList())
                        } else {
                            ApplicationToolExecutionEvent.Failed(ToolFailureReason.Unavailable)
                        },
                    )
                }
                is ApplicationToolDispatchResult.Rejected -> failure(result.reason.name)
            }
        } catch (_: RuntimeException) {
            failure("UNAVAILABLE")
        }
    }

    private fun failure(reason: String): String = JsonObject().apply {
        addProperty("ok", false)
        addProperty("reason", reason)
    }.toString().also { observer(ApplicationToolExecutionEvent.Failed(ToolFailureReason.Unavailable)) }

    companion object {
        const val DESCRIPTION =
            """{"name":"memory_write","description":"Save one concise fact or preference in local shared memory """ +
                """only when the user explicitly asks you to remember it. It will be available in later Chat """ +
                """and Voice Chat sessions. Tell the user what was saved and whether old entries were removed.",""" +
                """"parameters":{"type":"object","additionalProperties":false,""" +
                """"properties":{"text":{"type":"string","minLength":1,"maxLength":500,""" +
                """"description":"One self-contained memory entry."}},"required":["text"]}}"""
    }
}
