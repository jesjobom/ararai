package com.jesjobom.ararai.widget.launcher

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.jesjobom.ararai.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class LauncherWidgetRefreshReceiverTest {
    private lateinit var context: Context
    private lateinit var workManager: WorkManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setMinimumLoggingLevel(Log.DEBUG).setExecutor(SynchronousExecutor()).build(),
        )
        workManager = WorkManager.getInstance(context)
    }

    @Test
    fun `bound launcher refresh dispatches one run despite repeated taps`() {
        val appWidgetId = shadowOf(AppWidgetManager.getInstance(context))
            .createWidget(LauncherWidgetProvider::class.java, R.layout.launcher_widget)
        LauncherWidgetBindings(context).put(appWidgetId, "widget-1")
        val intent = refreshIntent(appWidgetId)

        LauncherWidgetRefreshReceiver().onReceive(context, intent)
        LauncherWidgetRefreshReceiver().onReceive(context, intent)

        val work = workManager.getWorkInfosForUniqueWork("managed-widget-launcher-refresh-widget-1").get()
        assertEquals(1, work.size)
    }

    @Test
    fun `unknown Android instance cannot enqueue refresh`() {
        LauncherWidgetRefreshReceiver().onReceive(context, refreshIntent(987654))

        assertTrue(workManager.getWorkInfosForUniqueWork("managed-widget-launcher-refresh-widget-1").get().isEmpty())
    }

    private fun refreshIntent(appWidgetId: Int) = Intent(context, LauncherWidgetRefreshReceiver::class.java)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
}
