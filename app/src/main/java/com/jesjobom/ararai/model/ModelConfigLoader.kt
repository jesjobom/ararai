package com.jesjobom.ararai.model

import android.content.Context
import androidx.annotation.RawRes

class ModelConfigLoader(
    private val context: Context,
    @param:RawRes private val resourceId: Int,
) {
    fun load(): ModelConfig {
        val raw =
            context.resources
                .openRawResource(resourceId)
                .bufferedReader()
                .use { it.readText() }
        return ModelConfigParser.parse(raw)
    }

    fun loadCatalog(): ModelCatalog {
        val raw =
            context.resources
                .openRawResource(resourceId)
                .bufferedReader()
                .use { it.readText() }
        return ModelConfigParser.parseCatalog(raw)
    }
}
