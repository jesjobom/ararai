package com.jesjobom.ararai.widget.managed

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.jesjobom.ararai.widget.runtime.WidgetProgramParser
import com.jesjobom.ararai.widget.runtime.WidgetProgramValidationResult
import kotlinx.coroutines.CancellationException
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
        val request = PeriodicWorkRequestBuilder<ManagedWidgetWorker>(interval, TimeUnit.HOURS)
            .setInputData(Data.Builder().putString(ManagedWidgetWorker.KEY_WIDGET_ID, definition.id).build())
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(if (requiresNetwork) NetworkType.CONNECTED else NetworkType.NOT_REQUIRED)
                    .build(),
            )
            .addTag(MANAGED_WIDGET_PERIODIC_WORK_TAG)
            .addTag(managedWidgetWorkTag(definition.id))
            .build()
        workManager.enqueueUniquePeriodicWork(
            managedWidgetWorkName(definition.id),
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

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
    suspend fun executeManagedWidget(widgetId: String)
}

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
            provider.executeManagedWidget(widgetId)
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: RuntimeException) {
            Result.failure()
        }
    }

    companion object {
        const val KEY_WIDGET_ID = "widget_id"
    }
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
