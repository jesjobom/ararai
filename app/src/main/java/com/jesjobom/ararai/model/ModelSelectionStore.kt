package com.jesjobom.ararai.model

import android.content.Context

interface ModelSelectionStore {
    fun selectedModelId(): String?
    fun saveSelectedModelId(modelId: String)
}

class InMemoryModelSelectionStore(
    initialModelId: String? = null,
) : ModelSelectionStore {
    private var modelId = initialModelId

    override fun selectedModelId(): String? = modelId

    override fun saveSelectedModelId(modelId: String) {
        this.modelId = modelId
    }
}

class SharedPreferencesModelSelectionStore(
    context: Context,
) : ModelSelectionStore {
    private val preferences = context.getSharedPreferences("ararai_preferences", Context.MODE_PRIVATE)

    override fun selectedModelId(): String? =
        preferences.getString(KEY_SELECTED_MODEL_ID, null)

    override fun saveSelectedModelId(modelId: String) {
        preferences.edit().putString(KEY_SELECTED_MODEL_ID, modelId).apply()
    }

    private companion object {
        const val KEY_SELECTED_MODEL_ID = "selected_model_id"
    }
}
