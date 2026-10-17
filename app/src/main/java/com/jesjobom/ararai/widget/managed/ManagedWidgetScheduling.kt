package com.jesjobom.ararai.widget.managed

import android.content.Context
import android.net.ConnectivityManager
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.jesjobom.ararai.widget.runtime.WidgetProgramParser
import com.jesjobom.ararai.widget.runtime.WidgetProgramValidationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

internal interface ManagedWidgetScheduler {
    fun reconcile(
        definition: ManagedWidgetDefinition,
        revision: WidgetProgramRevision,
    )

    fun cancel(widgetId: String)

    fun cancelUnknown(knownWidgetIds: Set<String>)
}

internal class WorkManagerManagedWidgetScheduler(
    private val workManager: WorkManager,
    private val networkToolIds: Set<String>,
) : ManagedWidgetScheduler {
    @Suppress("ReturnCount")
    override fun reconcile(
        definition: ManagedWidgetDefinition,
        revision: WidgetProgramRevision,
    ) {
        require(definition.id == revision.widgetId)
        if (definition.activeRevision != revision.revision) {
            cancel(definition.id)
            return
        }
        val interval = definition.periodicIntervalHours
        if (
            !definition.enabled ||
            interval == null ||
            interval !in ManagedWidgetPolicy.SUPPORTED_PERIODIC_INTERVAL_HOURS
        ) {
            cancel(definition.id)
            return
        }
        val program = WidgetProgramParser.parse(revision.manifestJson, revision.source)
        if (program !is WidgetProgramValidationResult.Valid) {
            cancel(definition.id)
            return
        }
        val requiresNetwork = program.program.manifest.capabilities.tools.any { it.id in networkToolIds }
        val workName = managedWidgetWorkName(definition.id)
        val scheduleTag = scheduleTag(revision, interval, requiresNetwork)
        val activeWork = workManager.getWorkInfosForUniqueWork(workName).get().filter { work ->
            work.state in setOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED)
        }
        // UPDATE advances the generation and can stop a worker that cold-started this process.
        if (activeWork.size == 1 && scheduleTag in activeWork.single().tags) return
        val request = PeriodicWorkRequestBuilder<ManagedWidgetWorker>(interval, TimeUnit.HOURS)
            .setInputData(
                Data.Builder()
                    .putString(ManagedWidgetWorker.KEY_WIDGET_ID, definition.id)
                    .putBoolean(ManagedWidgetWorker.KEY_REQUIRES_NETWORK, requiresNetwork)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
            // Samsung may block a cached UID's network while CONNECTED prevents its job from starting.
            // Let the worker start, then wait for this UID's network access before calling any tool.
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build())
            .addTag(MANAGED_WIDGET_PERIODIC_WORK_TAG)
            .addTag(managedWidgetWorkTag(definition.id))
            .addTag(scheduleTag)
            .build()
        workManager.enqueueUniquePeriodicWork(
            workName,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    private fun scheduleTag(revision: WidgetProgramRevision, interval: Long, requiresNetwork: Boolean): String = listOf(
        "managed-widget-schedule-v2",
        revision.revision,
        interval,
        requiresNetwork,
        revision.programDigest,
    ).joinToString(":")

    override fun cancel(widgetId: String) {
        requireValidWidgetId(widgetId)
        workManager.cancelUniqueWork(managedWidgetWorkName(widgetId))
    }

    override fun cancelUnknown(knownWidgetIds: Set<String>) {
        knownWidgetIds.forEach(::requireValidWidgetId)
        workManager.getWorkInfosByTag(MANAGED_WIDGET_PERIODIC_WORK_TAG).get().forEach { work ->
            val widgetId = work.tags
                .asSequence()
                .mapNotNull(::managedWidgetIdFromWorkTag)
                .singleOrNull()
            if (widgetId == null || widgetId !in knownWidgetIds) workManager.cancelWorkById(work.id)
        }
    }
}

interface ManagedWidgetExecutionProvider {
    suspend fun executeManagedWidget(widgetId: String): ManagedWidgetWorkerOutcome
}

enum class ManagedWidgetWorkerOutcome { Completed, Retry }

internal class ManagedWidgetWorker(
    appContext: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(appContext, parameters) {
    @Suppress("ReturnCount")
    override suspend fun doWork(): Result {
        val widgetId = inputData.getString(KEY_WIDGET_ID)?.takeIf(WIDGET_ID_PATTERN::matches)
            ?: return Result.failure()
        val provider = applicationContext as? ManagedWidgetExecutionProvider ?: return Result.failure()
        return try {
            if (inputData.getBoolean(KEY_REQUIRES_NETWORK, false) && !awaitWidgetNetworkAccess(::networkAvailable)) {
                return Result.retry()
            }
            when (provider.executeManagedWidget(widgetId)) {
                ManagedWidgetWorkerOutcome.Completed -> Result.success()
                ManagedWidgetWorkerOutcome.Retry -> Result.retry()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: RuntimeException) {
            Result.failure()
        }
    }

    companion object {
        const val KEY_WIDGET_ID = "widget_id"
        const val KEY_REQUIRES_NETWORK = "requires_network"
    }

    @Suppress("DEPRECATION")
    private fun networkAvailable(): Boolean {
        val connectivity = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        return connectivity?.activeNetworkInfo?.isConnected == true
    }
}

/** The UID can remain network-blocked briefly after JobScheduler starts its background process. */
internal suspend fun awaitWidgetNetworkAccess(
    isAvailable: () -> Boolean,
    maxChecks: Int = 20,
    checkIntervalMillis: Long = 250L,
): Boolean {
    repeat(maxChecks) {
        if (isAvailable()) return true
        delay(checkIntervalMillis)
    }
    return isAvailable()
}

internal class ManagedWidgetManualRefresh(
    private val coordinator: ManagedWidgetExecutionCoordinator,
) {
    suspend fun refresh(widgetId: String): ManagedWidgetExecutionStatus = coordinator.execute(widgetId)
}

internal class ManagedWidgetScheduleController(
    private val repository: ManagedWidgetRepository,
    private val scheduler: ManagedWidgetScheduler,
) {
    suspend fun createConfirmed(candidate: ConfirmedWidgetRevision): ManagedWidgetDefinition {
        val definition = repository.createConfirmed(candidate)
        reconcile(definition.id)
        return definition
    }

    suspend fun replaceActiveRevision(
        widgetId: String,
        candidate: ConfirmedWidgetRevision,
    ): ManagedWidgetDefinition {
        val definition = repository.replaceActiveRevision(widgetId, candidate)
        reconcile(widgetId)
        return definition
    }

    suspend fun reconcile(widgetId: String) {
        val definition = repository.listDefinitions().firstOrNull { it.id == widgetId }
        val revision = definition?.let { repository.activeRevision(widgetId) }
        if (definition == null || revision == null) {
            scheduler.cancel(widgetId)
        } else {
            scheduler.reconcile(definition, revision)
        }
    }

    suspend fun setEnabled(
        widgetId: String,
        enabled: Boolean,
    ): ManagedWidgetDefinition {
        val definition = repository.setEnabled(widgetId, enabled)
        reconcile(widgetId)
        return definition
    }

    suspend fun delete(widgetId: String) {
        repository.delete(widgetId)
        scheduler.cancel(widgetId)
    }
}

internal class ManagedWidgetStartupReconciler(
    private val repository: ManagedWidgetRepository,
    private val scheduler: ManagedWidgetScheduler,
) {
    suspend fun reconcile() {
        val definitions = repository.listDefinitions()
        definitions.forEach { definition ->
            val revision = repository.activeRevision(definition.id)
            if (revision == null) {
                scheduler.cancel(definition.id)
            } else {
                scheduler.reconcile(definition, revision)
            }
        }
        scheduler.cancelUnknown(definitions.mapTo(mutableSetOf(), ManagedWidgetDefinition::id))
    }
}

internal fun managedWidgetWorkName(widgetId: String): String {
    requireValidWidgetId(widgetId)
    return "managed-widget-periodic-$widgetId"
}

internal fun managedWidgetWorkTag(widgetId: String): String {
    requireValidWidgetId(widgetId)
    return "$MANAGED_WIDGET_WORK_TAG_PREFIX$widgetId"
}

private fun managedWidgetIdFromWorkTag(tag: String): String? = tag
    .takeIf { it.startsWith(MANAGED_WIDGET_WORK_TAG_PREFIX) }
    ?.removePrefix(MANAGED_WIDGET_WORK_TAG_PREFIX)
    ?.takeIf(WIDGET_ID_PATTERN::matches)

private const val MANAGED_WIDGET_WORK_TAG_PREFIX = "managed-widget-id:"
private const val MANAGED_WIDGET_PERIODIC_WORK_TAG = "managed-widget-periodic"
