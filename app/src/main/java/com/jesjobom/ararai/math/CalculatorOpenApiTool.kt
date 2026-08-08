@file:Suppress("MaxLineLength", "ReturnCount")

package com.jesjobom.ararai.math

import com.google.ai.edge.litertlm.OpenApiTool
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

sealed interface CalculatorExecutionEvent {
    data object Started : CalculatorExecutionEvent
    data class Finished(val failure: MathFailureReason? = null) : CalculatorExecutionEvent
}

class CalculatorOpenApiTool(engine: LocalMathEngine) : OpenApiTool {
    private val turn = CalculatorToolTurn(engine)
    fun beginTurn(observer: (CalculatorExecutionEvent) -> Unit = {}) = turn.beginTurn(observer)
    override fun getToolDescriptionJsonString(): String = CalculatorToolTurn.DESCRIPTION
    override fun execute(paramsJsonString: String): String = turn.execute(paramsJsonString)

    companion object {
        const val DESCRIPTION = CalculatorToolTurn.DESCRIPTION
    }
}

internal class CalculatorToolTurn(private val engine: LocalMathEngine) {
    private val calls = AtomicInteger()
    private val observer = AtomicReference<(CalculatorExecutionEvent) -> Unit>({})

    fun beginTurn(observer: (CalculatorExecutionEvent) -> Unit = {}) {
        calls.set(0)
        this.observer.set(observer)
    }

    fun execute(paramsJsonString: String): String {
        if (calls.incrementAndGet() > MAX_CALLS_PER_TURN) return failure(MathFailureReason.ComplexityLimit, "CALL_LIMIT_REACHED")
        observer.get()(CalculatorExecutionEvent.Started)
        val expression = parse(paramsJsonString)
            ?: return failure(MathFailureReason.InvalidExpression, "INVALID_ARGUMENTS")
        return when (val result = runBlocking { engine.evaluate(expression) }) {
            is MathEvaluationResult.Success -> JsonObject().apply {
                addProperty("ok", true)
                addProperty("value", result.value)
                addProperty("precision", result.kind.name.lowercase())
            }.toString().also { observer.get()(CalculatorExecutionEvent.Finished()) }
            is MathEvaluationResult.Failure -> failure(result.reason, result.reason.name.uppercase())
        }
    }

    private fun parse(raw: String): String? = runCatching { JsonParser.parseString(raw) }.getOrNull()
        ?.takeIf { it.isJsonObject }?.asJsonObject
        ?.takeIf { it.keySet() == setOf("expression") }
        ?.get("expression")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

    private fun failure(reason: MathFailureReason, code: String): String = JsonObject().apply {
        addProperty("ok", false)
        addProperty("error", code)
    }.toString().also { observer.get()(CalculatorExecutionEvent.Finished(reason)) }

    companion object {
        const val MAX_CALLS_PER_TURN = 3
        const val DESCRIPTION = """{"name":"calculator","description":"Evaluate a bounded numeric expression locally on-device.","parameters":{"type":"object","additionalProperties":false,"properties":{"expression":{"type":"string","maxLength":512}},"required":["expression"]}}"""
    }
}
