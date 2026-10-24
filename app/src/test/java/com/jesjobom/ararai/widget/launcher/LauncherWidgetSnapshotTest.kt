package com.jesjobom.ararai.widget.launcher

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.jesjobom.ararai.widget.managed.ManagedWidgetDefinition
import com.jesjobom.ararai.widget.managed.ManagedWidgetStatus
import com.jesjobom.ararai.widget.managed.WidgetPresentationCache
import com.jesjobom.ararai.widget.runtime.WidgetPresentationCodec
import com.jesjobom.ararai.widget.runtime.WidgetPresentationNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class LauncherWidgetSnapshotTest {
    @Test
    fun `separates first heading from remaining text and a validated link`() {
        val presentation = WidgetPresentationNode.Card(
            WidgetPresentationNode.Column(
                listOf(
                    WidgetPresentationNode.Text("  Today  ", "neutral"),
                    WidgetPresentationNode.Row(
                        listOf(
                            WidgetPresentationNode.Value("42", "neutral"),
                            WidgetPresentationNode.Icon("info"),
                        ),
                    ),
                    WidgetPresentationNode.HttpsLink(
                        "Read article",
                        "https://en.wikipedia.org/wiki/Ada_Lovelace",
                        "today",
                        "page.url",
                    ),
                ),
            ),
        )
        val snapshot = LauncherWidgetSnapshotProjector.project(definition(), cache(presentation))

        assertEquals("Daily card", snapshot.widgetName)
        assertEquals("Today", snapshot.headline)
        assertEquals("42", snapshot.body)
        assertEquals("Read article", snapshot.link?.label)
        assertEquals("https://en.wikipedia.org/wiki/Ada_Lovelace", snapshot.link?.url)
        assertEquals(LauncherWidgetState.Ready, snapshot.state)
        assertEquals(1000L, snapshot.updatedAtMillis)
    }

    @Test
    fun `retains saved content with stale or disabled status`() {
        val saved = cache(WidgetPresentationNode.Text("Previous result", "neutral"))

        assertEquals(
            LauncherWidgetState.Stale,
            LauncherWidgetSnapshotProjector.project(definition(lastAttemptAtMillis = 2000L), saved).state,
        )
        assertEquals(
            LauncherWidgetState.Stale,
            LauncherWidgetSnapshotProjector.project(definition(activeRevision = 2), saved).state,
        )
        val disabled = LauncherWidgetSnapshotProjector.project(definition(enabled = false), saved)
        assertEquals("Previous result", disabled.headline)
        assertEquals(LauncherWidgetState.Disabled, disabled.state)
    }

    @Test
    fun `invalid cache and missing definition never reach launcher body`() {
        val invalid = WidgetPresentationCache("widget-1", 1, "{}", 1000L)
        val empty = LauncherWidgetSnapshotProjector.project(definition(), invalid)

        assertEquals("", empty.body)
        assertEquals("", empty.headline)
        assertNull(empty.link)
        assertEquals(LauncherWidgetState.Empty, empty.state)
        assertEquals(LauncherWidgetState.Unavailable, LauncherWidgetSnapshotProjector.project(null, invalid).state)
    }

    @Test
    fun `keeps full saved content for resized layouts`() {
        val text = "😀".repeat(510)
        val snapshot = LauncherWidgetSnapshotProjector.project(
            definition(),
            cache(
                WidgetPresentationNode.Column(
                    listOf(
                        WidgetPresentationNode.Text("Event", "neutral"),
                        WidgetPresentationNode.Text(text, "neutral"),
                    ),
                ),
            ),
        )

        assertEquals(510, snapshot.body.codePointCount(0, snapshot.body.length))
        assertEquals(text, snapshot.body)
    }

    @Test
    fun `does not expose a cached link outside the Wikipedia allowlist`() {
        val presentation = WidgetPresentationNode.Column(
            listOf(
                WidgetPresentationNode.Text("Event", "neutral"),
                WidgetPresentationNode.HttpsLink("Unsafe", "https://evil.example/wiki/Event", "today", "page.url"),
                WidgetPresentationNode.HttpsLink(
                    "Read article",
                    "https://pt.wikipedia.org/wiki/Evento",
                    "today",
                    "page.url",
                ),
            ),
        )

        val snapshot = LauncherWidgetSnapshotProjector.project(definition(), cache(presentation))
        assertEquals("Read article", snapshot.link?.label)
        assertTrue(snapshot.link?.url?.startsWith("https://pt.wikipedia.org/wiki/") == true)
    }

    @Test
    fun `bindings survive recreation and can be removed`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val first = LauncherWidgetBindings(context)
        first.put(4789, "widget-1")

        assertEquals("widget-1", LauncherWidgetBindings(context).get(4789))
        first.remove(4789)
        assertNull(LauncherWidgetBindings(context).get(4789))
    }

    private fun definition(
        enabled: Boolean = true,
        activeRevision: Int = 1,
        lastAttemptAtMillis: Long = 1000L,
    ) = ManagedWidgetDefinition(
        id = "widget-1",
        displayName = "Daily card",
        enabled = enabled,
        periodicIntervalHours = 24,
        activeRevision = activeRevision,
        consentDigest = "b".repeat(64),
        status = ManagedWidgetStatus.Ready,
        createdAtMillis = 1000L,
        updatedAtMillis = 1000L,
        lastAttemptAtMillis = lastAttemptAtMillis,
        lastSuccessAtMillis = 1000L,
    )

    private fun cache(node: WidgetPresentationNode) = WidgetPresentationCache(
        widgetId = "widget-1",
        revision = 1,
        presentationJson = WidgetPresentationCodec.encode(node),
        completedAtMillis = 1000L,
    )
}
