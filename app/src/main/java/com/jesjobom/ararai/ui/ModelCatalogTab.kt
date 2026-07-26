package com.jesjobom.ararai.ui

import com.jesjobom.ararai.model.ManagedModelItem
import com.jesjobom.ararai.model.ModelPurpose
import com.jesjobom.ararai.model.ModelTask
import com.jesjobom.ararai.model.supportsPurpose
import com.jesjobom.ararai.model.supportsTask

internal enum class ModelCatalogTab(val label: String) {
    Chat("Chat"),
    Transcription("Transcription"),
}

internal fun List<ManagedModelItem>.forTab(tab: ModelCatalogTab): List<ManagedModelItem> {
    val compatible = filter { item ->
        when (tab) {
            ModelCatalogTab.Chat ->
                item.config.supportsPurpose(ModelPurpose.Chat) &&
                    !item.config.supportsTask(ModelTask.Transcription)
            ModelCatalogTab.Transcription -> item.config.supportsTask(ModelTask.Transcription)
        }
    }
    return compatible
        .groupBy { it.config.family }
        .toList()
        .sortedWith(
            compareBy<Pair<String, List<ManagedModelItem>>>(
                { (_, members) -> members.minOfOrNull { it.config.expectedBytes ?: Long.MAX_VALUE } },
                { it.first },
            ),
        )
        .flatMap { (_, members) ->
            members.sortedWith(
                compareBy<ManagedModelItem>(
                    { it.config.expectedBytes ?: Long.MAX_VALUE },
                    { it.config.name },
                    { it.config.id },
                ),
            )
        }
}

internal fun ManagedModelItem.isRecommendedFor(availableMemoryBytes: Long?): Boolean {
    val required = config.recommendedFreeRamBytes ?: return false
    return availableMemoryBytes != null && required <= availableMemoryBytes
}
