package com.jesjobom.ararai.widget.managed

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.jesjobom.ararai.widget.runtime.WidgetProgramParserTest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class ManagedWidgetSchedulingTest {
    private lateinit var context: Context
    private lateinit var workManager: WorkManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder()
                .setMinimumLoggingLevel(Log.DEBUG)
                .setExecutor(SynchronousExecutor())
                .build(),
        )
        workManager = WorkManager.getInstance(context)
    }

    @Test
    fun `reconciliation creates replaces and cancels exactly one constrained periodic work`() {
        val scheduler = scheduler()
        val revision = revision()
        val daily = definition(interval = 24)

        scheduler.reconcile(daily, revision)
        scheduler.reconcile(daily, revision)

        var work = currentWork()
        assertEquals(1, work.size)
        assertEquals(WorkInfo.State.ENQUEUED, work.single().state)
        assertEquals(NetworkType.CONNECTED, work.single().constraints.requiredNetworkType)
        assertTrue(managedWidgetWorkTag(WIDGET_ID) in work.single().tags)

        scheduler.reconcile(daily.copy(periodicIntervalHours = 6), revision)
        work = currentWork()
        assertEquals(1, work.size)
        assertEquals(WorkInfo.State.ENQUEUED, work.single().state)

        scheduler.reconcile(daily.copy(enabled = false), revision)
        assertTrue(currentWork().all { it.state == WorkInfo.State.CANCELLED })
    }

    @Test
    fun `invalid program or absent schedule cancels instead of enqueuing`() {
        val scheduler = scheduler()
        val invalid = revision().copy(manifestJson = "{}")

        scheduler.reconcile(definition(interval = null), revision())
        scheduler.reconcile(definition(interval = 2), revision())
        scheduler.reconcile(definition(interval = 24), invalid)

        assertTrue(currentWork().none { it.state == WorkInfo.State.ENQUEUED })
    }

    @Test
    fun `constraints come from registered network tool identities`() {
        WorkManagerManagedWidgetScheduler(workManager, emptySet()).reconcile(definition(interval = 24), revision())

        assertEquals(NetworkType.NOT_REQUIRED, currentWork().single().constraints.requiredNetworkType)
    }

    @Test
    fun `startup pruning cancels orphan work and keeps known work`() {
        val scheduler = scheduler()
        scheduler.reconcile(definition(interval = 24), revision())

        scheduler.cancelUnknown(setOf(WIDGET_ID))
        assertEquals(WorkInfo.State.ENQUEUED, currentWork().single().state)

        scheduler.cancelUnknown(emptySet())
        assertTrue(currentWork().all { it.state == WorkInfo.State.CANCELLED })
    }

    private fun currentWork() = workManager.getWorkInfosForUniqueWork(managedWidgetWorkName(WIDGET_ID)).get()

    private fun scheduler() = WorkManagerManagedWidgetScheduler(workManager, setOf("wikipedia_pages"))

    private fun definition(interval: Long?) = ManagedWidgetDefinition(
        id = WIDGET_ID,
        displayName = "On this day",
        enabled = true,
        periodicIntervalHours = interval,
        activeRevision = 1,
        consentDigest = "b".repeat(64),
        status = ManagedWidgetStatus.Ready,
        createdAtMillis = 1_000L,
        updatedAtMillis = 1_000L,
    )

    private fun revision() = WidgetProgramRevision(
        widgetId = WIDGET_ID,
        revision = 1,
        manifestJson = WidgetProgramParserTest.validManifest().replace("weather_lookup", "wikipedia_pages"),
        source = WidgetProgramParserTest.SOURCE,
        programDigest = "a".repeat(64),
        createdAtMillis = 1_000L,
    )

    private companion object {
        const val WIDGET_ID = "widget-1"
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(application = ManagedWidgetWorkerTest.TestApplication::class, sdk = [35])
class ManagedWidgetWorkerTest {
    private lateinit var application: TestApplication

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        application.executions.clear()
        application.failure = null
    }

    @Test
    fun `worker resolves application scoped execution after process construction`() = runTest {
        val result = worker(WIDGET_ID).doWork()

        assertEquals(ListenableWorker.Result.success().javaClass, result.javaClass)
        assertEquals(listOf(WIDGET_ID), application.executions)
    }

    @Test
    fun `worker converts internal failure to controlled terminal result`() = runTest {
        application.failure = IllegalStateException("private provider detail")

        val result = worker(WIDGET_ID).doWork()

        assertEquals(ListenableWorker.Result.failure().javaClass, result.javaClass)
        assertEquals(listOf(WIDGET_ID), application.executions)
    }

    @Test(expected = CancellationException::class)
    fun `worker preserves cooperative cancellation`() = runTest {
        application.failure = CancellationException("stopped")

        worker(WIDGET_ID).doWork()
    }

    @Test
    fun `worker fails closed for malformed or missing identity`() = runTest {
        val missing = worker(null).doWork()
        val malformed = worker("../other").doWork()

        assertEquals(ListenableWorker.Result.failure().javaClass, missing.javaClass)
        assertEquals(ListenableWorker.Result.failure().javaClass, malformed.javaClass)
        assertTrue(application.executions.isEmpty())
    }

    @Test
    fun `worker composition owns no model dependency`() {
        val dependencies = ManagedWidgetWorker::class.java.declaredConstructors
            .flatMap { it.parameterTypes.toList() }
            .map(Class<*>::getName)

        assertFalse(dependencies.any { it.contains("LocalLlm") || it.contains("Model") })
    }

    private fun worker(widgetId: String?): ManagedWidgetWorker {
        val input = Data.Builder().apply { widgetId?.let { putString(ManagedWidgetWorker.KEY_WIDGET_ID, it) } }.build()
        return TestListenableWorkerBuilder<ManagedWidgetWorker>(application)
            .setInputData(input)
            .build()
    }

    class TestApplication :
        Application(),
        ManagedWidgetExecutionProvider {
        val executions = mutableListOf<String>()
        var failure: RuntimeException? = null

        override suspend fun executeManagedWidget(widgetId: String) {
            executions += widgetId
            failure?.let { throw it }
        }
    }

    private companion object {
        const val WIDGET_ID = "widget-1"
    }
}
