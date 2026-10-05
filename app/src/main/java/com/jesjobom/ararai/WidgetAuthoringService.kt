package com.jesjobom.ararai

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.jesjobom.ararai.model.ModelStartupState
import com.jesjobom.ararai.model.WIDGET_AUTHORING_PIPELINE_V1
import com.jesjobom.ararai.widget.managed.AcceptWidgetAuthoringStageResult
import com.jesjobom.ararai.widget.managed.AndroidWidgetAuthoringDeviceState
import com.jesjobom.ararai.widget.managed.RunWidgetAuthoringStageResult
import com.jesjobom.ararai.widget.managed.WidgetAuthoringDeferralReason
import com.jesjobom.ararai.widget.managed.WidgetAuthoringInferenceCodec
import com.jesjobom.ararai.widget.managed.WidgetAuthoringJobController
import com.jesjobom.ararai.widget.managed.WidgetAuthoringJobOutcome
import com.jesjobom.ararai.widget.managed.WidgetAuthoringJobPresenter
import com.jesjobom.ararai.widget.managed.WidgetAuthoringJobState
import com.jesjobom.ararai.widget.managed.WidgetAuthoringNotificationAction
import com.jesjobom.ararai.widget.managed.WidgetAuthoringNotificationProjector
import com.jesjobom.ararai.widget.managed.WidgetAuthoringProgress
import com.jesjobom.ararai.widget.managed.WidgetAuthoringSessionSnapshot
import com.jesjobom.ararai.widget.managed.forAuthoringCharacterization
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the process alive while a widget authoring job
 * is active and owns the persistent progress/cancel notification. The job
 * controller drives this service through [WidgetAuthoringNotificationPresenter].
 */
class WidgetAuthoringService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val controller
        get() = (application as ArarAiApplication).widgetAuthoringJobs

    private val workflow
        get() = (application as ArarAiApplication).widgetAuthoringWorkflowCoordinator

    private val presenter
        get() = (application as ArarAiApplication).widgetAuthoringNotificationPresenter

    private val workflowPresenter
        get() = (application as ArarAiApplication).resumableWidgetAuthoringNotificationPresenter

    override fun onBind(intent: Intent?): IBinder? = null

    @Suppress("ReturnCount")
    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ACTION_REFRESH -> {
                handleForegroundRefresh(startId)
                return START_NOT_STICKY
            }
            ACTION_CANCEL -> {
                controller.cancel()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_CANCEL_RESUMABLE -> {
                if (intent.matchesWidgetAuthoringSession(workflow.activeSession.value)) {
                    workflow.cancelActiveAttempt()
                }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_CONTINUE_RESUMABLE -> {
                handleContinue(intent)
                return START_NOT_STICKY
            }
            ACTION_RETRY_RESUMABLE -> {
                handleRetry(intent)
                return START_NOT_STICKY
            }
        }
        workflow.activeSession.value?.let { snapshot ->
            if (WidgetAuthoringNotificationProjector.project(snapshot).ongoing) {
                startInForeground(workflowPresenter.notification(snapshot))
                return START_NOT_STICKY
            }
        }
        val state = controller.state.value
        if (state == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        startInForeground(presenter.notification(state))
        return START_NOT_STICKY
    }

    private fun handleForegroundRefresh(startId: Int) {
        val snapshot = workflow.activeSession.value
        val legacyState = controller.state.value
        val notification = when {
            snapshot != null -> workflowPresenter.notification(snapshot)
            legacyState != null -> presenter.notification(legacyState)
            else -> workflowPresenter.startingNotification()
        }
        startInForeground(notification)
        when (widgetAuthoringForegroundDisposition(snapshot, legacyState)) {
            WidgetAuthoringForegroundDisposition.Keep -> Unit
            WidgetAuthoringForegroundDisposition.DetachAndStop -> {
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelfResult(startId)
            }
            WidgetAuthoringForegroundDisposition.RemoveAndStop -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelfResult(startId)
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun handleContinue(intent: Intent) {
        val snapshot = workflow.activeSession.value
        if (!intent.matchesWidgetAuthoringSession(snapshot)) {
            stopSelf()
            return
        }
        serviceScope.launch {
            runCatching { workflow.acceptCurrent(intent.requireActionId()) }
                .onSuccess { result ->
                    if (result is AcceptWidgetAuthoringStageResult.AssemblyInvalid) {
                        workflowPresenter.onStateChanged(workflow.activeSession.value)
                    }
                }
            stopSelf()
        }
    }

    private fun handleRetry(intent: Intent) {
        val snapshot = workflow.activeSession.value
        if (!intent.matchesWidgetAuthoringSession(snapshot)) {
            stopSelf()
            return
        }
        val app = application as ArarAiApplication
        val item = app.modelController.state.value.models.firstOrNull { it.config.id == snapshot?.session?.modelId }
        val available = item?.state as? ModelStartupState.Available
        if (snapshot == null || item == null || available == null) {
            stopSelf()
            return
        }
        serviceScope.launch {
            val executionModel = if (
                available.model.toolCapabilities.supportsAuthoringProtocol(WIDGET_AUTHORING_PIPELINE_V1) ||
                !BuildConfig.DEBUG
            ) {
                available.model
            } else {
                available.model.forAuthoringCharacterization()
            }
            runCatching {
                workflow.retryCurrent(
                    model = executionModel,
                    inference = WidgetAuthoringInferenceCodec.decode(snapshot.session.inferenceConfigJson),
                    modelArtifactDigest = item.config.sha256,
                    actionId = intent.requireActionId(),
                )
            }.onSuccess { result ->
                if (result !is RunWidgetAuthoringStageResult.Submitted) {
                    workflowPresenter.onStateChanged(workflow.activeSession.value)
                }
            }
            stopSelf()
        }
    }

    private fun startInForeground(notification: Notification) {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    companion object {
        internal const val ACTION_REFRESH = "com.jesjobom.ararai.action.REFRESH_WIDGET_AUTHORING"
        private const val ACTION_CANCEL = "com.jesjobom.ararai.action.CANCEL_WIDGET_AUTHORING"
        private const val ACTION_CANCEL_RESUMABLE = "com.jesjobom.ararai.action.CANCEL_WIDGET_AUTHORING_STAGE"
        private const val ACTION_CONTINUE_RESUMABLE = "com.jesjobom.ararai.action.CONTINUE_WIDGET_AUTHORING_STAGE"
        private const val ACTION_RETRY_RESUMABLE = "com.jesjobom.ararai.action.RETRY_WIDGET_AUTHORING_STAGE"
        internal const val EXTRA_SESSION_ID = "session_id"
        internal const val EXTRA_SESSION_REVISION = "session_revision"
        private const val EXTRA_ACTION_ID = "action_id"
        internal const val NOTIFICATION_ID = 1002

        fun cancelIntent(context: Context): Intent = Intent(context, WidgetAuthoringService::class.java)
            .setAction(ACTION_CANCEL)

        fun refreshIntent(context: Context): Intent = Intent(context, WidgetAuthoringService::class.java)
            .setAction(ACTION_REFRESH)

        internal fun workflowActionIntent(
            context: Context,
            snapshot: WidgetAuthoringSessionSnapshot,
            action: WidgetAuthoringNotificationAction,
        ): Intent = Intent(context, WidgetAuthoringService::class.java)
            .setAction(
                when (action) {
                    WidgetAuthoringNotificationAction.Continue -> ACTION_CONTINUE_RESUMABLE
                    WidgetAuthoringNotificationAction.Retry -> ACTION_RETRY_RESUMABLE
                    WidgetAuthoringNotificationAction.Cancel -> ACTION_CANCEL_RESUMABLE
                    WidgetAuthoringNotificationAction.Review -> error("Review opens the activity")
                },
            )
            .putExtra(EXTRA_SESSION_ID, snapshot.session.id)
            .putExtra(EXTRA_SESSION_REVISION, snapshot.session.revision)
            .putExtra(EXTRA_ACTION_ID, "notification-${snapshot.session.revision}-${action.name.lowercase()}")
    }

    private fun Intent.requireActionId(): String = requireNotNull(getStringExtra(EXTRA_ACTION_ID))
}

internal enum class WidgetAuthoringForegroundDisposition {
    Keep,
    DetachAndStop,
    RemoveAndStop,
}

internal fun widgetAuthoringForegroundDisposition(
    snapshot: WidgetAuthoringSessionSnapshot?,
    legacyState: WidgetAuthoringJobState?,
): WidgetAuthoringForegroundDisposition = when {
    snapshot != null && WidgetAuthoringNotificationProjector.project(snapshot).ongoing ->
        WidgetAuthoringForegroundDisposition.Keep
    snapshot != null -> WidgetAuthoringForegroundDisposition.DetachAndStop
    legacyState is WidgetAuthoringJobState.Queued ||
        legacyState is WidgetAuthoringJobState.Running ||
        legacyState is WidgetAuthoringJobState.Deferred -> WidgetAuthoringForegroundDisposition.Keep
    else -> WidgetAuthoringForegroundDisposition.RemoveAndStop
}

internal fun Intent.matchesWidgetAuthoringSession(
    snapshot: WidgetAuthoringSessionSnapshot?,
): Boolean = snapshot != null &&
    getStringExtra(WidgetAuthoringService.EXTRA_SESSION_ID) == snapshot.session.id &&
    getIntExtra(WidgetAuthoringService.EXTRA_SESSION_REVISION, -1) == snapshot.session.revision

/**
 * Publishes job-state changes as the persistent authoring notification and
 * keeps [WidgetAuthoringService] running for the lifetime of an active job.
 */
internal class WidgetAuthoringNotificationPresenter(
    context: Context,
) : WidgetAuthoringJobPresenter {
    private val appContext = context.applicationContext
    private val notificationManager = NotificationManagerCompat.from(appContext)

    init {
        createNotificationChannel()
    }

    override fun onStateChanged(state: WidgetAuthoringJobState) {
        when (state) {
            is WidgetAuthoringJobState.Queued,
            is WidgetAuthoringJobState.Running,
            is WidgetAuthoringJobState.Deferred,
            -> {
                androidx.core.content.ContextCompat.startForegroundService(
                    appContext,
                    WidgetAuthoringService.refreshIntent(appContext),
                )
                notificationManager.notifyIfAllowed(
                    appContext,
                    WidgetAuthoringService.NOTIFICATION_ID,
                    notification(state),
                )
            }
            is WidgetAuthoringJobState.Succeeded<*>,
            is WidgetAuthoringJobState.Failed,
            is WidgetAuthoringJobState.Cancelled,
            -> {
                appContext.stopService(android.content.Intent(appContext, WidgetAuthoringService::class.java))
                notificationManager.cancel(WidgetAuthoringService.NOTIFICATION_ID)
            }
        }
    }

    internal fun notification(state: WidgetAuthoringJobState): Notification {
        val cancelPendingIntent = PendingIntent.getService(
            appContext,
            0,
            WidgetAuthoringService.cancelIntent(appContext),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val openAppPendingIntent = PendingIntent.getActivity(
            appContext,
            0,
            appContext.packageManager.getLaunchIntentForPackage(appContext.packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat
            .Builder(appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(appContext.getString(R.string.widget_authoring_notification_title))
            .setContentText(description(state))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppPendingIntent)
            .addAction(
                0,
                appContext.getString(R.string.action_cancel),
                cancelPendingIntent,
            )
            .build()
    }

    private fun description(state: WidgetAuthoringJobState): String = when (state) {
        is WidgetAuthoringJobState.Queued -> appContext.getString(R.string.widget_authoring_notification_queued)
        is WidgetAuthoringJobState.Running -> stageDescription(state.progress)
        is WidgetAuthoringJobState.Deferred ->
            appContext.getString(
                when (state.reason) {
                    WidgetAuthoringDeferralReason.ThermalState ->
                        R.string.widget_authoring_deferred_thermal
                    WidgetAuthoringDeferralReason.LowMemory ->
                        R.string.widget_authoring_deferred_memory
                },
            )
        is WidgetAuthoringJobState.Succeeded<*> ->
            appContext.getString(R.string.widget_authoring_notification_succeeded)
        is WidgetAuthoringJobState.Failed ->
            appContext.getString(R.string.widget_authoring_notification_failed)
        is WidgetAuthoringJobState.Cancelled ->
            appContext.getString(R.string.widget_authoring_notification_cancelled)
    }

    private fun stageDescription(progress: WidgetAuthoringProgress?): String = when (progress) {
        null,
        is WidgetAuthoringProgress.AnalyzingFeasibility,
        -> appContext.getString(R.string.widget_authoring_stage_feasibility)
        is WidgetAuthoringProgress.WaitingForDeviceRecovery ->
            appContext.getString(R.string.widget_authoring_stage_device_recovery)
        is WidgetAuthoringProgress.ReloadingModel ->
            appContext.getString(R.string.widget_authoring_stage_model_reload)
        is WidgetAuthoringProgress.DesigningAlgorithm ->
            appContext.getString(R.string.widget_authoring_stage_algorithm)
        is WidgetAuthoringProgress.GeneratingCall ->
            appContext.getString(R.string.widget_authoring_stage_call, progress.index, progress.count)
        is WidgetAuthoringProgress.GeneratingPlan ->
            appContext.getString(R.string.widget_authoring_stage_plan)
        is WidgetAuthoringProgress.GeneratingPresentation ->
            appContext.getString(R.string.widget_authoring_stage_render)
        is WidgetAuthoringProgress.ValidatingAssembly ->
            appContext.getString(R.string.widget_authoring_stage_validation)
        is WidgetAuthoringProgress.Repairing ->
            appContext.getString(
                R.string.widget_authoring_stage_repair,
                progress.stage.name,
                progress.repair,
                progress.maximum,
            )
        is WidgetAuthoringProgress.DraftReady ->
            appContext.getString(R.string.widget_authoring_stage_ready)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                appContext.getString(R.string.widget_authoring_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "widget_authoring"
    }
}

internal class ResumableWidgetAuthoringNotificationPresenter(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val notificationManager = NotificationManagerCompat.from(appContext)
    private var serviceStarted = false

    init {
        createNotificationChannel()
    }

    fun onStateChanged(snapshot: WidgetAuthoringSessionSnapshot?) {
        if (snapshot == null) {
            stopOwnedService()
            notificationManager.cancel(WidgetAuthoringService.NOTIFICATION_ID)
            return
        }
        val state = WidgetAuthoringNotificationProjector.project(snapshot)
        if (state.ongoing) {
            androidx.core.content.ContextCompat.startForegroundService(
                appContext,
                WidgetAuthoringService.refreshIntent(appContext),
            )
            serviceStarted = true
        } else {
            stopOwnedService()
        }
        notificationManager.notifyIfAllowed(
            appContext,
            WidgetAuthoringService.NOTIFICATION_ID,
            notification(snapshot),
        )
    }

    private fun stopOwnedService() {
        if (!serviceStarted) return
        appContext.stopService(Intent(appContext, WidgetAuthoringService::class.java))
        serviceStarted = false
    }

    internal fun notification(snapshot: WidgetAuthoringSessionSnapshot): Notification {
        val state = WidgetAuthoringNotificationProjector.project(snapshot)
        val openApp = PendingIntent.getActivity(
            appContext,
            snapshot.session.revision,
            Intent(appContext, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_WIDGET_AUTHORING, true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(appContext.getString(R.string.widget_authoring_notification_title))
            .setContentText(description(snapshot))
            .setOngoing(state.ongoing)
            .setAutoCancel(!state.ongoing)
            .setOnlyAlertOnce(state.ongoing)
            .setContentIntent(openApp)
        state.actions.forEach { action ->
            val pendingIntent = if (action == WidgetAuthoringNotificationAction.Review) {
                openApp
            } else {
                PendingIntent.getService(
                    appContext,
                    10_000 + snapshot.session.revision * 10 + action.ordinal,
                    WidgetAuthoringService.workflowActionIntent(appContext, snapshot, action),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            }
            builder.addAction(0, appContext.getString(action.label()), pendingIntent)
        }
        return builder.build()
    }

    internal fun startingNotification(): Notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_notify_sync)
        .setContentTitle(appContext.getString(R.string.widget_authoring_notification_title))
        .setContentText(appContext.getString(R.string.widget_authoring_notification_queued))
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .build()

    private fun description(snapshot: WidgetAuthoringSessionSnapshot): String = when (snapshot.session.status) {
        com.jesjobom.ararai.widget.managed.WidgetAuthoringSessionStatus.AttemptQueued ->
            appContext.getString(R.string.widget_authoring_notification_stage_queued, stageName(snapshot))
        com.jesjobom.ararai.widget.managed.WidgetAuthoringSessionStatus.AttemptDeferred ->
            appContext.getString(R.string.widget_authoring_notification_stage_deferred, stageName(snapshot))
        com.jesjobom.ararai.widget.managed.WidgetAuthoringSessionStatus.AttemptRunning ->
            appContext.getString(R.string.widget_authoring_notification_stage_running, stageName(snapshot))
        com.jesjobom.ararai.widget.managed.WidgetAuthoringSessionStatus.AwaitingReview ->
            appContext.getString(R.string.widget_authoring_notification_stage_review, stageName(snapshot))
        com.jesjobom.ararai.widget.managed.WidgetAuthoringSessionStatus.AwaitingRetry ->
            appContext.getString(R.string.widget_authoring_notification_stage_retry, stageName(snapshot))
        com.jesjobom.ararai.widget.managed.WidgetAuthoringSessionStatus.AwaitingStage ->
            appContext.getString(R.string.widget_authoring_notification_stage_ready, stageName(snapshot))
        com.jesjobom.ararai.widget.managed.WidgetAuthoringSessionStatus.ReadyForPreview ->
            appContext.getString(R.string.widget_authoring_notification_preview_ready)
        com.jesjobom.ararai.widget.managed.WidgetAuthoringSessionStatus.Completed ->
            appContext.getString(R.string.widget_authoring_notification_completed)
    }

    private fun stageName(snapshot: WidgetAuthoringSessionSnapshot): String = snapshot.session.currentStage.wireValue

    private fun WidgetAuthoringNotificationAction.label(): Int = when (this) {
        WidgetAuthoringNotificationAction.Review -> R.string.widget_authoring_notification_action_review
        WidgetAuthoringNotificationAction.Continue -> R.string.widget_authoring_notification_action_continue
        WidgetAuthoringNotificationAction.Retry -> R.string.widget_authoring_notification_action_retry
        WidgetAuthoringNotificationAction.Cancel -> R.string.action_cancel
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    appContext.getString(R.string.widget_authoring_notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
    }

    private companion object {
        const val CHANNEL_ID = "widget_authoring"
    }
}

private fun NotificationManagerCompat.notifyIfAllowed(
    context: Context,
    notificationId: Int,
    notification: Notification,
) {
    if (
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    ) {
        notify(notificationId, notification)
    }
}
