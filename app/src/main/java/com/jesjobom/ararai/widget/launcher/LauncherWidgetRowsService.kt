package com.jesjobom.ararai.widget.launcher

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.jesjobom.ararai.ArarAiApplication
import com.jesjobom.ararai.R
import com.jesjobom.ararai.knowledge.validWidgetExternalUrl
import kotlinx.coroutines.runBlocking

/** Reads only the saved, validated presentation; launcher rendering never executes a widget. */
class LauncherWidgetRowsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        return LauncherWidgetRowsFactory(applicationContext) { loadLauncherWidgetRows(applicationContext, appWidgetId) }
    }
}

private fun loadLauncherWidgetRows(context: Context, appWidgetId: Int): List<LauncherWidgetRow> = runBlocking {
    val app = context.applicationContext as? ArarAiApplication ?: return@runBlocking emptyList()
    val widgetId = LauncherWidgetBindings(app).get(appWidgetId) ?: return@runBlocking emptyList()
    val repository = app.managedWidgetApplicationServices.repository
    val definition = repository.listDefinitions().firstOrNull { it.id == widgetId }
        ?: return@runBlocking emptyList()
    LauncherWidgetSnapshotProjector.project(definition, repository.cachedPresentation(widgetId)).rows
}

internal class LauncherWidgetRowsFactory(
    private val context: Context,
    private val loadRows: () -> List<LauncherWidgetRow>,
) : RemoteViewsService.RemoteViewsFactory {
    @Volatile
    private var rows: List<LauncherWidgetRow> = emptyList()

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        rows = try {
            loadRows()
        } catch (_: Exception) {
            emptyList()
        }
    }

    override fun onDestroy() {
        rows = emptyList()
    }

    override fun getCount(): Int = rows.size

    override fun getViewAt(position: Int): RemoteViews? {
        val row = rows.getOrNull(position) ?: return null
        return RemoteViews(context.packageName, R.layout.launcher_widget_row).apply {
            when (row) {
                is LauncherWidgetRow.Text -> setTextViewText(R.id.launcher_widget_row_text, row.value)
                is LauncherWidgetRow.Link -> {
                    setTextViewText(R.id.launcher_widget_row_text, row.value.label)
                    setTextColor(R.id.launcher_widget_row_text, android.graphics.Color.rgb(141, 209, 255))
                    setOnClickFillInIntent(
                        R.id.launcher_widget_row_text,
                        Intent().putExtra(LauncherWidgetLinkActivity.EXTRA_URL, row.value.url),
                    )
                }
            }
        }
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = false
}

/** Mutable collection fill-ins terminate here, where the destination policy is checked again. */
class LauncherWidgetLinkActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent?.getStringExtra(EXTRA_URL)
        val appWidgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (url != null && validWidgetExternalUrl(url) && isSavedLink(appWidgetId, url)) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE))
            } catch (_: ActivityNotFoundException) {
                // A browser is not installed or has been disabled.
            }
        }
        finish()
    }

    private fun isSavedLink(appWidgetId: Int, url: String): Boolean = loadLauncherWidgetRows(this, appWidgetId)
        .filterIsInstance<LauncherWidgetRow.Link>()
        .any { it.value.url == url }

    companion object {
        const val EXTRA_URL = "com.jesjobom.ararai.launcher.EXTRA_URL"
    }
}
