package com.jesjobom.ararai.widget.launcher

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.jesjobom.ararai.widget.managed.ManagedWidgetWorker

/** PendingIntent target for an explicit user refresh; never exported to other apps. */
class LauncherWidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (appWidgetId !in LauncherWidgetUpdates.widgetIds(context)) return
        val widgetId = LauncherWidgetBindings(context).get(appWidgetId) ?: return
        val request = OneTimeWorkRequestBuilder<ManagedWidgetWorker>()
            .setInputData(
                Data.Builder()
                    .putString(ManagedWidgetWorker.KEY_WIDGET_ID, widgetId)
                    .putBoolean(ManagedWidgetWorker.KEY_DETECT_NETWORK, true)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "managed-widget-launcher-refresh-$widgetId",
            ExistingWorkPolicy.KEEP,
            request,
        )
    }
}
