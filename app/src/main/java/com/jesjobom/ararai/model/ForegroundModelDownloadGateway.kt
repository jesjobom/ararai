package com.jesjobom.ararai.model

import android.content.Context
import com.jesjobom.ararai.ModelDownloadService

class ForegroundModelDownloadGateway(
    private val context: Context,
) : ModelDownloadCommandGateway {
    override fun start(modelId: String, replaceExisting: Boolean) {
        context.startForegroundService(ModelDownloadService.downloadIntent(context, modelId, replaceExisting))
    }

    override fun cancel(modelId: String) {
        context.startService(ModelDownloadService.cancelIntent(context, modelId))
    }
}
