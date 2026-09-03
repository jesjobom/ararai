@file:Suppress("MaxLineLength", "ReturnCount")

package com.jesjobom.ararai.math

import com.google.ai.edge.litertlm.OpenApiTool
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.jesjobom.ararai.chat.CALCULATOR_TOOL_NAME
import com.jesjobom.ararai.tools.ApplicationToolConsumer
import com.jesjobom.ararai.tools.ApplicationToolDispatchResult
import com.jesjobom.ararai.tools.ApplicationToolDispatcher
import com.jesjobom.ararai.tools.ApplicationToolInvocation
import com.jesjobom.ararai.tools.ApplicationToolRejection
import com.jesjobom.ararai.tools.CURRENT_TOOL_CONTRACT_VERSION
import com.jesjobom.ararai.tools.mathResult
import com.jesjobom.ararai.tools.singleCalculatorDispatcher
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

sealed interface CalculatorExecutionEvent {
    data object Started : CalculatorExecutionEvent
    data class Finished(val failure: MathFailureReason? = null) : CalculatorExecutionEvent
}

class CalculatorOpenApiTool(
    dispatcher: ApplicationToolDispatcher,
    verifiedModelToolIds: Set<String>,
) : OpenApiTool {
    constructor(engine: LocalMathEngine) : this(
        dispatcher = singleCalculatorDispatcher(engine),
        verifiedModelToolIds = setOf(CALCULATOR_TOOL_NAME),
    )

    private val turn = CalculatorToolTurn(dispatcher, verifiedModelToolIds)
    fun beginTurn(observer: (CalculatorExecutionEvent) -> Unit = {}) = turn.beginTurn(observer)
    override fun getToolDescriptionJsonString(): String = CalculatorToolTurn.DESCRIPTION
    override fun execute(paramsJsonString: String): String = turn.execute(paramsJsonString)

    companion object {
        const val DESCRIPTION = CalculatorToolTurn.DESCRIPTION
    }
}

internal class CalculatorToolTurn(
    private val dispatcher: ApplicationToolDispatcher,
    private val verifiedModelToolIds: Set<String>,
) {
    constructor(engine: LocalMathEngine) : this(
        dispatcher = singleCalculatorDispatcher(engine),
        verifiedModelToolIds = setOf(CALCULATOR_TOOL_NAME),
    )

    private val calls = AtomicInteger()
    private val observer = AtomicReference<(CalculatorExecutionEvent) -> Unit>({})

    fun beginTurn(observer: (CalculatorExecutionEvent) -> Unit = {}) {
        calls.set(0)
        this.observer.set(observer)
    }

    fun execute(paramsJsonString: String): String {
        if (calls.incrementAndGet() > MAX_CALLS_PER_TURN) return failure(MathFailureReason.ComplexityLimit, "CALL_LIMIT_REACHED")
        observer.get()(CalculatorExecutionEvent.Started)
        return when (
            val dispatched = runBlocking {
                dispatcher.execute(
                    ApplicationToolInvocation(
                        id = CALCULATOR_TOOL_NAME,
                        version = CURRENT_TOOL_CONTRACT_VERSION,
                        consumer = ApplicationToolConsumer.Model,
                        argumentsJson = paramsJsonString,
                        verifiedModelToolIds = verifiedModelToolIds,
                    ),
                )
            }
        ) {
            is ApplicationToolDispatchResult.Executed -> when (val result = dispatched.mathResult()) {
                is MathEvaluationResult.Success -> JsonObject().apply {
                    addProperty("ok", true)
                    addProperty("value", result.value)
                    addProperty("precision", result.kind.name.lowercase())
                }.toString().also { observer.get()(CalculatorExecutionEvent.Finished()) }
                is MathEvaluationResult.Failure -> failure(result.reason, result.reason.name.uppercase())
            }
            is ApplicationToolDispatchResult.Rejected -> {
                val reason = dispatched.reason.toMathFailureReason()
                val code = if (dispatched.reason == ApplicationToolRejection.InvalidArguments) {
                    "INVALID_ARGUMENTS"
                } else {
                    reason.name.uppercase()
                }
                failure(reason, code)
            }
        }
    }

    private fun failure(reason: MathFailureReason, code: String): String = JsonObject().apply {
        addProperty("ok", false)
        addProperty("error", code)
    }.toString().also { observer.get()(CalculatorExecutionEvent.Finished(reason)) }

    private fun ApplicationToolRejection.toMathFailureReason(): MathFailureReason = when (this) {
        ApplicationToolRejection.InvalidArguments -> MathFailureReason.InvalidExpression
        ApplicationToolRejection.TimedOut -> MathFailureReason.TimedOut
        ApplicationToolRejection.Cancelled -> MathFailureReason.Cancelled
        ApplicationToolRejection.UnknownTool,
        ApplicationToolRejection.UnsupportedVersion,
        ApplicationToolRejection.Disabled,
        ApplicationToolRejection.NotConfigured,
        ApplicationToolRejection.IneligibleConsumer,
        ApplicationToolRejection.UnsupportedModel,
        ApplicationToolRejection.Unavailable,
        -> MathFailureReason.Unavailable
    }

    companion object {
        const val MAX_CALLS_PER_TURN = 3
        const val DESCRIPTION = """{"name":"calculator","description":"Evaluate a bounded numeric expression locally on-device.","parameters":{"type":"object","additionalProperties":false,"properties":{"expression":{"type":"string","maxLength":512}},"required":["expression"]}}"""
    }
}
