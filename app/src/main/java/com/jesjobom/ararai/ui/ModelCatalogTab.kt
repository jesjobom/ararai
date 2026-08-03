package com.jesjobom.ararai.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.jesjobom.ararai.R
import com.jesjobom.ararai.model.ManagedModelItem
import com.jesjobom.ararai.model.ModelPurpose
import com.jesjobom.ararai.model.ModelTask
import com.jesjobom.ararai.model.supportsPurpose
import com.jesjobom.ararai.model.supportsTask

internal enum class ModelCatalogTab {
    Chat,
    Transcription,
}

@Composable
internal fun ModelCatalogTab.label(): String = when (this) {
    ModelCatalogTab.Chat -> stringResource(R.string.models_chat_tab)
    ModelCatalogTab.Transcription -> stringResource(R.string.models_transcription_tab)
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
