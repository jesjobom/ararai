@file:Suppress("MaxLineLength") // LiteRT tool description is a machine-readable JSON literal.

package com.jesjobom.ararai.weather

import android.util.Log
import com.google.ai.edge.litertlm.OpenApiTool
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.jesjobom.ararai.tools.ApplicationToolConsumer
import com.jesjobom.ararai.tools.ApplicationToolDispatchResult
import com.jesjobom.ararai.tools.ApplicationToolDispatcher
import com.jesjobom.ararai.tools.ApplicationToolInvocation
import com.jesjobom.ararai.tools.WEATHER_FORECAST_TOOL_NAME
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger

/** Model bridge; the application registry remains the authority for enablement and execution. */
class WeatherOpenApiTool(
    private val dispatcher: ApplicationToolDispatcher,
    private val verifiedModelToolIds: Set<String>,
) : OpenApiTool {
    private val calls = AtomicInteger()

    fun beginTurn() {
        calls.set(0)
    }

    override fun getToolDescriptionJsonString(): String = DESCRIPTION

    override fun execute(paramsJsonString: String): String {
        Log.d(LOG_TAG, "Weather model tool started")
        val startedAt = System.nanoTime()
        if (calls.incrementAndGet() > 2) {
            Log.d(LOG_TAG, "Weather model tool call limit reached")
            return failure("CALL_LIMIT_REACHED")
        }
        return try {
            when (
                val result = runBlocking {
                    dispatcher.execute(
                        ApplicationToolInvocation(
                            id = WEATHER_FORECAST_TOOL_NAME,
                            version = 1,
                            consumer = ApplicationToolConsumer.Model,
                            argumentsJson = paramsJsonString,
                            verifiedModelToolIds = verifiedModelToolIds,
                        ),
                    )
                }
            ) {
                is ApplicationToolDispatchResult.Executed -> result.payloadJson.also { payload ->
                    val data = runCatching { JsonParser.parseString(payload).asJsonObject }.getOrNull()
                    val reason = data?.get("reason")?.asString?.takeIf { value ->
                        WeatherFailure.entries.any { it.name == value }
                    }
                    val outcome = reason ?: "success"
                    Log.d(
                        LOG_TAG,
                        "Weather model tool completed: outcome=$outcome elapsedMs=" +
                            "${(System.nanoTime() - startedAt) / 1_000_000}",
                    )
                }.let(::weatherPayloadForModel)
                is ApplicationToolDispatchResult.Rejected -> failure(result.reason.name).also {
                    Log.d(LOG_TAG, "Weather model tool rejected: reason=${result.reason.name}")
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Log.d(LOG_TAG, "Weather model tool failed")
            failure("Unavailable")
        }
    }

    private fun failure(reason: String): String = JsonObject().apply {
        addProperty("kind", "failure")
        addProperty("reason", reason)
    }.toString()

    private companion object {
        const val LOG_TAG = "ArarAI.Weather"
        const val DESCRIPTION = """{"name":"weather_forecast","description":"Get current conditions or forecast from Open-Meteo for an explicitly named place. If the user did not provide a place, ask for one instead of calling this tool. The app has no access to the device's current location. The named place is sent to Open-Meteo.","parameters":{"type":"object","additionalProperties":false,"properties":{"location":{"type":"string","minLength":1,"maxLength":120,"description":"Required city and optional region/country explicitly provided by the user."},"period":{"type":"string","enum":["now","today","tomorrow","next_7_days"]}},"required":["location","period"]}}"""
    }
}
