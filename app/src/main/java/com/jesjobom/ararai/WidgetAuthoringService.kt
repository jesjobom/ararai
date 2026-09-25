package com.jesjobom.ararai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.jesjobom.ararai.widget.managed.AndroidWidgetAuthoringDeviceState
import com.jesjobom.ararai.widget.managed.WidgetAuthoringDeferralReason
import com.jesjobom.ararai.widget.managed.WidgetAuthoringJobController
import com.jesjobom.ararai.widget.managed.WidgetAuthoringJobOutcome
import com.jesjobom.ararai.widget.managed.WidgetAuthoringJobPresenter
import com.jesjobom.ararai.widget.managed.WidgetAuthoringJobState
import com.jesjobom.ararai.widget.managed.WidgetAuthoringProgress

/**
 * Foreground service that keeps the process alive while a widget authoring job
 * is active and owns the persistent progress/cancel notification. The job
 * controller drives this service through [WidgetAuthoringNotificationPresenter].
 */
class WidgetAuthoringService : Service() {
    private val controller
        get() = (application as ArarAiApplication).widgetAuthoringJobs

    private val presenter
        get() = (application as ArarAiApplication).widgetAuthoringNotificationPresenter

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (intent?.action == ACTION_CANCEL) {
            controller.cancel()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val state = controller.state.value
        if (state == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        startInForeground(presenter.notification(state))
        return START_NOT_STICKY
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
        private const val ACTION_CANCEL = "com.jesjobom.ararai.action.CANCEL_WIDGET_AUTHORING"
        internal const val NOTIFICATION_ID = 1002

        fun cancelIntent(context: Context): Intent = Intent(context, WidgetAuthoringService::class.java)
            .setAction(ACTION_CANCEL)

        fun refreshIntent(context: Context): Intent = Intent(context, WidgetAuthoringService::class.java)
    }
}

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
                notificationManager.notify(
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