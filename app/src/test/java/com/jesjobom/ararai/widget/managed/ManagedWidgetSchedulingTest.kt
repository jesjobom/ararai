package com.jesjobom.ararai.widget.managed

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
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
import org.robolectric.Shadows.shadowOf
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
        assertEquals(NetworkType.NOT_REQUIRED, work.single().constraints.requiredNetworkType)
        assertTrue(managedWidgetWorkTag(WIDGET_ID) in work.single().tags)
        assertTrue(work.single().tags.any { it.startsWith("managed-widget-schedule-v2:1:24:true:") })

        scheduler.reconcile(daily.copy(periodicIntervalHours = 6), revision)
        work = currentWork()
        assertEquals(1, work.size)
        assertEquals(WorkInfo.State.ENQUEUED, work.single().state)

        scheduler.reconcile(daily.copy(enabled = false), revision)
        assertTrue(currentWork().all { it.state == WorkInfo.State.CANCELLED })
    }

    @Test
    fun `unchanged startup reconciliation preserves the active job generation`() {
        val scheduler = scheduler()
        val definition = definition(interval = 1)
        val revision = revision()

        scheduler.reconcile(definition, revision)
        val before = currentWork().single()
        scheduler.reconcile(definition, revision)
        val after = currentWork().single()

        assertEquals(before.id, after.id)
        assertEquals(before.generation, after.generation)
    }

    @Test
    fun `changed interval revision or network requirement updates the existing job`() {
        val scheduler = scheduler()
        val revision = revision()
        scheduler.reconcile(definition(interval = 1), revision)
        var previous = currentWork().single()

        scheduler.reconcile(definition(interval = 6), revision)
        var changed = currentWork().single()
        assertEquals(previous.id, changed.id)
        assertEquals(previous.generation + 1, changed.generation)
        previous = changed

        val nextRevision = revision.copy(revision = 2, programDigest = "c".repeat(64))
        scheduler.reconcile(definition(interval = 6).copy(activeRevision = 2), nextRevision)
        changed = currentWork().single()
        assertEquals(previous.id, changed.id)
        assertEquals(previous.generation + 1, changed.generation)
        previous = changed

        WorkManagerManagedWidgetScheduler(workManager, emptySet())
            .reconcile(definition(interval = 6).copy(activeRevision = 2), nextRevision)
        changed = currentWork().single()
        assertEquals(previous.id, changed.id)
        assertEquals(previous.generation + 1, changed.generation)
        assertEquals(NetworkType.NOT_REQUIRED, changed.constraints.requiredNetworkType)
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
        application.outcome = ManagedWidgetWorkerOutcome.Completed
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

    @Test
    fun `worker retries a controlled transient tool failure`() = runTest {
        application.outcome = ManagedWidgetWorkerOutcome.Retry

        val result = worker(WIDGET_ID).doWork()

        assertEquals(ListenableWorker.Result.retry().javaClass, result.javaClass)
        assertEquals(listOf(WIDGET_ID), application.executions)
    }

    @Test
    fun `worker waits for UID network access before invoking a network tool`() = runTest {
        val connectivity = application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        shadowOf(connectivity).setActiveNetworkInfo(null)

        val result = worker(WIDGET_ID, requiresNetwork = true).doWork()

        assertEquals(ListenableWorker.Result.retry().javaClass, result.javaClass)
        assertTrue(application.executions.isEmpty())
    }

    @Test
    fun `network readiness retries a transient UID block without calling the tool early`() = runTest {
        var checks = 0

        assertTrue(awaitWidgetNetworkAccess({ ++checks == 3 }, maxChecks = 3, checkIntervalMillis = 1))
        assertEquals(3, checks)
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

    private fun worker(widgetId: String?, requiresNetwork: Boolean = false): ManagedWidgetWorker {
        val input = Data.Builder().apply {
            widgetId?.let { putString(ManagedWidgetWorker.KEY_WIDGET_ID, it) }
            putBoolean(ManagedWidgetWorker.KEY_REQUIRES_NETWORK, requiresNetwork)
        }.build()
        return TestListenableWorkerBuilder<ManagedWidgetWorker>(application)
            .setInputData(input)
            .build()
    }

    class TestApplication :
        Application(),
        ManagedWidgetExecutionProvider {
        val executions = mutableListOf<String>()
        var failure: RuntimeException? = null
        var outcome = ManagedWidgetWorkerOutcome.Completed

        override suspend fun executeManagedWidget(widgetId: String): ManagedWidgetWorkerOutcome {
            executions += widgetId
            failure?.let { throw it }
            return outcome
        }
    }

    private companion object {
        const val WIDGET_ID = "widget-1"
    }
}
