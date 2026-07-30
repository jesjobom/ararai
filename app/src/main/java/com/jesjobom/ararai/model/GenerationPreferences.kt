package com.jesjobom.ararai.model

import android.content.Context
import com.jesjobom.ararai.engine.GenerationMetrics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class GenerationOverride(
    val contextTokens: Int? = null,
    val temperature: Float? = null,
)

data class GenerationSettingsState(
    val overrides: Map<String, GenerationOverride> = emptyMap(),
    val lastMetricsByModel: Map<String, GenerationMetrics> = emptyMap(),
)

enum class TemperaturePreset(
    val displayName: String,
    val value: Float,
) {
    Precise("Precise", 0.2f),
    Balanced("Balanced", 0.7f),
    Creative("Creative", 1.0f),
}

interface GenerationPreferences {
    val state: StateFlow<GenerationSettingsState>

    fun setContextTokens(modelId: String, value: Int?)

    fun setTemperature(modelId: String, value: Float?)

    fun restoreDefaults(modelId: String)

    fun recordMetrics(modelId: String, metrics: GenerationMetrics)
}

open class InMemoryGenerationPreferences(
    initial: GenerationSettingsState = GenerationSettingsState(),
) : GenerationPreferences {
    private val mutableState = MutableStateFlow(initial)
    override val state: StateFlow<GenerationSettingsState> = mutableState.asStateFlow()

    override fun setContextTokens(modelId: String, value: Int?) {
        require(value == null || value > 0) { "Context window must be positive" }
        updateOverride(modelId) { it.copy(contextTokens = value) }
    }

    override fun setTemperature(modelId: String, value: Float?) {
        require(value == null || (value.isFinite() && value >= 0f)) {
            "Temperature must be a finite non-negative number"
        }
        updateOverride(modelId) { it.copy(temperature = value) }
    }

    open override fun restoreDefaults(modelId: String) {
        mutableState.update { it.copy(overrides = it.overrides - modelId) }
    }

    override fun recordMetrics(modelId: String, metrics: GenerationMetrics) {
        mutableState.update {
            it.copy(lastMetricsByModel = it.lastMetricsByModel + (modelId to metrics))
        }
    }

    protected fun currentOverride(modelId: String): GenerationOverride {
        val existing = state.value.overrides[modelId]
        return existing ?: GenerationOverride()
    }

    protected open fun persistOverride(modelId: String, value: GenerationOverride?) = Unit

    private fun updateOverride(modelId: String, update: (GenerationOverride) -> GenerationOverride) {
        val next = update(currentOverride(modelId))
        val normalized = next.takeUnless { it.contextTokens == null && it.temperature == null }
        mutableState.update {
            it.copy(
                overrides =
                if (normalized == null) it.overrides - modelId else it.overrides + (modelId to normalized),
            )
        }
        persistOverride(modelId, normalized)
    }
}

class SharedPreferencesGenerationPreferences(context: Context) : InMemoryGenerationPreferences(loadInitial(context)) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun persistOverride(modelId: String, value: GenerationOverride?) {
        val editor = preferences.edit()
        val contextKey = contextKey(modelId)
        val temperatureKey = temperatureKey(modelId)
        if (value?.contextTokens == null) editor.remove(contextKey) else editor.putInt(contextKey, value.contextTokens)
        if (value?.temperature == null) {
            editor.remove(temperatureKey)
        } else {
            editor.putFloat(temperatureKey, value.temperature)
        }
        editor.apply()
    }

    override fun restoreDefaults(modelId: String) {
        super.restoreDefaults(modelId)
        preferences.edit().remove(contextKey(modelId)).remove(temperatureKey(modelId)).apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "generation_preferences"
        private const val CONTEXT_PREFIX = "context."
        private const val TEMPERATURE_PREFIX = "temperature."

        private fun contextKey(modelId: String) = "$CONTEXT_PREFIX$modelId"
        private fun temperatureKey(modelId: String) = "$TEMPERATURE_PREFIX$modelId"

        private fun loadInitial(context: Context): GenerationSettingsState {
            val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            val modelIds =
                preferences.all.keys.mapNotNull { key ->
                    when {
                        key.startsWith(CONTEXT_PREFIX) -> key.removePrefix(CONTEXT_PREFIX)
                        key.startsWith(TEMPERATURE_PREFIX) -> key.removePrefix(TEMPERATURE_PREFIX)
                        else -> null
                    }
                }.toSet()
            val overrides =
                modelIds.mapNotNull { modelId ->
                    val contextValue = preferences.getInt(contextKey(modelId), 0).takeIf { it > 0 }
                    val temperatureValue =
                        preferences.getFloat(temperatureKey(modelId), Float.NaN)
                            .takeIf { it.isFinite() && it >= 0f }
                    GenerationOverride(contextValue, temperatureValue)
                        .takeUnless { it.contextTokens == null && it.temperature == null }
                        ?.let { modelId to it }
                }.toMap()
            return GenerationSettingsState(overrides = overrides)
        }
    }
}

fun GenerationSettingsState.resolve(
    modelId: String,
    catalog: InferenceConfig,
): InferenceConfig {
    val override = overrides[modelId]
    return catalog.copy(
        contextTokens = override?.contextTokens ?: catalog.contextTokens,
        temperature = override?.temperature ?: catalog.temperature,
    )
}
