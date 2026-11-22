package com.jesjobom.ararai.widget.launcher

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.jesjobom.ararai.ArarAiApplication
import com.jesjobom.ararai.widget.managed.ConfirmedWidgetRevision
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = ArarAiApplication::class, sdk = [35])
class LauncherWidgetConfigureActivityTest {
    private val app: ArarAiApplication
        get() = ApplicationProvider.getApplicationContext()
    private val bindings get() = LauncherWidgetBindings(app)

    @Test
    fun `missing unknown and foreign widget ids are rejected`() {
        val foreignId = 50
        for (id in listOf(null, 49, foreignId)) {
            buildActivity(id).use { controller ->
                if (id == foreignId) {
                    val foreign = ComponentName("other.launcher", "other.launcher.Widget")
                    registerWidget(controller.get(), id, foreign)
                    assertEquals(foreign, AppWidgetManager.getInstance(controller.get()).getAppWidgetInfo(id).provider)
                }
                val activity = controller.create().get()
                assertTrue(activity.isFinishing)
                assertEquals(Activity.RESULT_CANCELED, shadowOf(activity).resultCode)
                if (id != null) assertNull(bindings.get(id))
            }
        }
    }

    @Test
    fun `opening and cancelling preserves the previous binding`() {
        val id = 51
        bindings.put(id, "original")

        buildActivity(id).use { controller ->
            registerWidget(controller.get(), id)
            val activity = controller.setup().get()
            assertFalse(activity.isFinishing)
            assertEquals("original", bindings.get(id))
            activity.onBackPressedDispatcher.onBackPressed()
            assertTrue(activity.isFinishing)
            assertEquals(Activity.RESULT_CANCELED, shadowOf(activity).resultCode)
            assertEquals("original", bindings.get(id))
        }
    }

    @Test
    fun `choosing a widget changes only the requested instance`() {
        val requested = 51
        val other = 52
        bindings.put(requested, "original")
        bindings.put(other, "other")
        val repository = app.managedWidgetApplicationServices.repository
        val selected = runBlocking {
            repository.createConfirmed(
                ConfirmedWidgetRevision("Selection", false, null, "{}", "", "0".repeat(64), "0".repeat(64)),
            )
        }

        buildActivity(requested).use { controller ->
            registerWidget(controller.get(), requested)
            registerWidget(controller.get(), other)
            val activity = controller.setup().get()
            assertFalse(activity.isFinishing)
            assertEquals("original", bindings.get(requested))
            runBlocking { activity.confirmSelection(requested, selected.id, repository) }

            assertTrue(activity.isFinishing)
            assertEquals(Activity.RESULT_OK, shadowOf(activity).resultCode)
            val result = shadowOf(activity).resultIntent
            assertEquals(requested, result.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1))
            assertEquals(selected.id, bindings.get(requested))
            assertEquals("other", bindings.get(other))
        }
    }

    private fun registerWidget(
        activity: Activity,
        id: Int,
        component: ComponentName = LauncherWidgetUpdates.provider(activity),
    ) {
        shadowOf(AppWidgetManager.getInstance(activity))
            .addBoundWidget(id, AppWidgetProviderInfo().apply { provider = component })
    }

    private fun buildActivity(id: Int?) = Robolectric.buildActivity(
        LauncherWidgetConfigureActivity::class.java,
        Intent(app, LauncherWidgetConfigureActivity::class.java)
            .setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
            .apply { if (id != null) putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id) },
    )
}
