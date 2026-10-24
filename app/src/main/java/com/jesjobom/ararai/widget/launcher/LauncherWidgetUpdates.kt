package com.jesjobom.ararai.widget.launcher

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.jesjobom.ararai.ArarAiApplication
import com.jesjobom.ararai.MainActivity
import com.jesjobom.ararai.R
import com.jesjobom.ararai.settings.SharedPreferencesApplicationLanguagePreferenceStore
import com.jesjobom.ararai.widget.managed.ManagedWidgetDefinition
import com.jesjobom.ararai.widget.managed.ManagedWidgetRepository
import java.text.DateFormat
import java.util.Date

internal object LauncherWidgetUpdates {
    fun request(context: Context) {
        if (widgetIds(context).isEmpty()) return
        WorkManager.getInstance(context).enqueueUniqueWork(
            "managed-widget-launcher-render",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<LauncherWidgetRenderWorker>().build(),
        )
    }

    fun widgetIds(context: Context): IntArray = AppWidgetManager.getInstance(context).getAppWidgetIds(provider(context))

    fun provider(context: Context): ComponentName = ComponentName(context, LauncherWidgetProvider::class.java)
}

class LauncherWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        LauncherWidgetUpdates.request(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val bindings = LauncherWidgetBindings(context)
        appWidgetIds.forEach(bindings::remove)
        super.onDeleted(context, appWidgetIds)
    }
}

internal class LauncherWidgetRenderWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        val app = applicationContext as? ArarAiApplication ?: return Result.failure()
        LauncherWidgetRenderer.updateAll(app, app.managedWidgetApplicationServices.repository)
        Result.success()
    } catch (_: RuntimeException) {
        Result.retry()
    }
}

internal object LauncherWidgetRenderer {
    suspend fun updateAll(context: Context, repository: ManagedWidgetRepository) {
        val definitions = repository.listDefinitions().associateBy(ManagedWidgetDefinition::id)
        val bindings = LauncherWidgetBindings(context)
        for (appWidgetId in LauncherWidgetUpdates.widgetIds(context)) {
            updateOne(context, appWidgetId, repository, bindings, definitions)
        }
    }

    suspend fun updateOne(context: Context, appWidgetId: Int, repository: ManagedWidgetRepository) {
        val definitions = repository.listDefinitions().associateBy(ManagedWidgetDefinition::id)
        updateOne(context, appWidgetId, repository, LauncherWidgetBindings(context), definitions)
    }

    private suspend fun updateOne(
        context: Context,
        appWidgetId: Int,
        repository: ManagedWidgetRepository,
        bindings: LauncherWidgetBindings,
        definitions: Map<String, ManagedWidgetDefinition>,
    ) {
        val widgetId = bindings.get(appWidgetId)
        val snapshot = if (widgetId == null) {
            LauncherWidgetSnapshot("", "", "", null, LauncherWidgetState.Unconfigured)
        } else {
            val definition = definitions[widgetId]
            LauncherWidgetSnapshotProjector.project(
                definition,
                definition?.let { repository.cachedPresentation(widgetId) },
            )
        }
        val language = SharedPreferencesApplicationLanguagePreferenceStore(context).language
        val localized = SharedPreferencesApplicationLanguagePreferenceStore.localizedContext(context, language)
        val views = RemoteViews(context.packageName, R.layout.launcher_widget).apply {
            setTextViewText(
                R.id.launcher_widget_title,
                snapshot.widgetName.ifBlank { localized.getString(R.string.app_name) },
            )
            setTextViewText(R.id.launcher_widget_headline, snapshot.headline)
            setViewVisibility(
                R.id.launcher_widget_headline,
                if (snapshot.headline.isBlank()) View.GONE else View.VISIBLE,
            )
            setTextViewText(
                R.id.launcher_widget_body,
                snapshot.body.ifBlank {
                    if (snapshot.headline.isBlank() && snapshot.link == null) {
                        fallbackBody(localized, snapshot.state)
                    } else {
                        ""
                    }
                },
            )
            setTextViewText(R.id.launcher_widget_link, snapshot.link?.label.orEmpty())
            setViewVisibility(R.id.launcher_widget_link, if (snapshot.link == null) View.GONE else View.VISIBLE)
            snapshot.link?.let { link ->
                setOnClickPendingIntent(R.id.launcher_widget_link, openLinkIntent(context, appWidgetId, link.url))
            }
            setTextViewText(R.id.launcher_widget_status, statusText(localized, snapshot))
            val canRefresh = widgetId?.let { definitions[it]?.enabled } == true
            setViewVisibility(R.id.launcher_widget_refresh, if (canRefresh) View.VISIBLE else View.GONE)
            if (canRefresh) {
                setOnClickPendingIntent(R.id.launcher_widget_refresh, refreshIntent(context, appWidgetId))
            }
            setOnClickPendingIntent(
                R.id.launcher_widget_root,
                openDetailIntent(context, appWidgetId, widgetId?.takeIf { definitions.containsKey(it) }),
            )
        }
        AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, views)
    }

    private fun fallbackBody(context: Context, state: LauncherWidgetState): String = context.getString(
        when (state) {
            LauncherWidgetState.Unconfigured -> R.string.widget_launcher_unconfigured
            LauncherWidgetState.Unavailable -> R.string.widget_launcher_unavailable
            else -> R.string.widget_launcher_empty
        },
    )

    private fun statusText(context: Context, snapshot: LauncherWidgetSnapshot): String = when (snapshot.state) {
        LauncherWidgetState.Unconfigured,
        LauncherWidgetState.Unavailable,
        LauncherWidgetState.Empty,
        -> ""
        LauncherWidgetState.Disabled -> context.getString(R.string.widget_launcher_disabled)
        LauncherWidgetState.Stale -> context.getString(R.string.widget_launcher_stale)
        LauncherWidgetState.NeedsAttention -> context.getString(R.string.widget_launcher_needs_attention)
        LauncherWidgetState.Ready -> snapshot.updatedAtMillis?.let { timestamp ->
            val locale = context.resources.configuration.locales[0]
            val date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale)
                .format(Date(timestamp))
            context.getString(R.string.widget_launcher_updated, date)
        }.orEmpty()
    }

    private fun openDetailIntent(context: Context, appWidgetId: Int, widgetId: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (widgetId == null) {
                putExtra(MainActivity.EXTRA_OPEN_WIDGETS, true)
            } else {
                putExtra(MainActivity.EXTRA_OPEN_WIDGET_DETAIL, widgetId)
            }
        }
        return PendingIntent.getActivity(
            context,
            appWidgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun openLinkIntent(context: Context, appWidgetId: Int, url: String): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        return PendingIntent.getActivity(
            context,
            appWidgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun refreshIntent(context: Context, appWidgetId: Int): PendingIntent {
        val intent = Intent(context, LauncherWidgetRefreshReceiver::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        return PendingIntent.getBroadcast(
            context,
            appWidgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
