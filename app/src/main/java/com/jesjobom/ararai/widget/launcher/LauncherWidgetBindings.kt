package com.jesjobom.ararai.widget.launcher

import android.appwidget.AppWidgetManager
import android.content.Context
import com.jesjobom.ararai.widget.managed.WIDGET_ID_PATTERN

internal class LauncherWidgetBindings(context: Context) {
    private val preferences = context.getSharedPreferences("launcher_widget_bindings", Context.MODE_PRIVATE)

    fun get(appWidgetId: Int): String? = preferences.getString(key(appWidgetId), null)
        ?.takeIf(WIDGET_ID_PATTERN::matches)

    fun put(appWidgetId: Int, managedWidgetId: String) {
        require(appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID)
        require(WIDGET_ID_PATTERN.matches(managedWidgetId))
        preferences.edit().putString(key(appWidgetId), managedWidgetId).apply()
    }

    fun remove(appWidgetId: Int) {
        preferences.edit().remove(key(appWidgetId)).apply()
    }

    private fun key(appWidgetId: Int) = "widget_$appWidgetId"
}
