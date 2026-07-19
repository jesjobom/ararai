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
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.jesjobom.ararai.model.ModelDownloadServiceController
import com.jesjobom.ararai.model.ModelStartupState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ModelDownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val ownedModelIds = linkedSetOf<String>()
    private val observedDownloadingIds = linkedSetOf<String>()
    private var hasReceivedCommand = false
    private var observation: Job? = null
    private var lastNotificationAtMillis = 0L
    private var lastNotifiedPercent = -1
    internal var controllerOverride: ModelDownloadServiceController? = null
    internal var elapsedRealtime: () -> Long = SystemClock::elapsedRealtime
    private val controller: ModelDownloadServiceController
        get() = controllerOverride ?: (application as ArarAiApplication).modelController

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startInForeground(preparingNotification())
        observation =
            scope.launch {
                controller.state.collectLatest { state ->
                    val active = state.models.filter { it.config.id in ownedModelIds }
                    active
                        .filter { it.state is ModelStartupState.Downloading }
                        .forEach { observedDownloadingIds += it.config.id }
                    active
                        .filter {
                            it.config.id in observedDownloadingIds && it.state !is ModelStartupState.Downloading
                        }.forEach {
                            ownedModelIds.remove(it.config.id)
                            observedDownloadingIds.remove(it.config.id)
                        }
                    val downloading = active.firstOrNull { it.state is ModelStartupState.Downloading }
                    if (downloading != null) {
                        val downloadState = downloading.state as ModelStartupState.Downloading
                        if (shouldNotify(downloadState)) {
                            notificationManager.notify(
                                NOTIFICATION_ID,
                                downloadNotification(downloading.config.name, downloadState, downloading.config.id),
                            )
                        }
                    } else if (hasReceivedCommand && ownedModelIds.isEmpty()) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val modelId = intent?.getStringExtra(EXTRA_MODEL_ID) ?: return START_NOT_STICKY
        hasReceivedCommand = true
        when (intent.action) {
            ACTION_DOWNLOAD -> {
                ownedModelIds += modelId
                controller.executeBackgroundDownload(modelId, intent.getBooleanExtra(EXTRA_REPLACE, false))
            }
            ACTION_CANCEL -> {
                ownedModelIds.remove(modelId)
                observedDownloadingIds.remove(modelId)
                controller.executeBackgroundCancel(modelId)
            }
        }
        return START_REDELIVER_INTENT
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        ownedModelIds.toList().forEach(controller::executeBackgroundCancel)
        ownedModelIds.clear()
        observedDownloadingIds.clear()
        observation?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun startInForeground(notification: Notification) {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
    }

    private fun preparingNotification(): Notification = NotificationCompat
        .Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setContentTitle("Preparing model download")
        .setOngoing(true)
        .setContentIntent(openAppIntent())
        .build()

    private fun downloadNotification(
        name: String,
        state: ModelStartupState.Downloading,
        modelId: String,
    ): Notification {
        val builder =
            NotificationCompat
                .Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("Downloading $name")
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(openAppIntent())
                .addAction(0, "Cancel", cancelPendingIntent(modelId))
        val total = state.totalBytes
        if (total != null && total > 0L) {
            val percent = progressPercent(state)
            builder
                .setContentText("$percent%")
                .setProgress(100, percent, false)
        } else {
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    private fun shouldNotify(state: ModelStartupState.Downloading): Boolean {
        val now = elapsedRealtime()
        val percent = progressPercent(state)
        val shouldNotify = lastNotifiedPercent < 0 || percent >= 100 || now - lastNotificationAtMillis >= 500L
        if (shouldNotify) {
            lastNotificationAtMillis = now
            lastNotifiedPercent = percent
        }
        return shouldNotify
    }

    private fun progressPercent(state: ModelStartupState.Downloading): Int {
        val total = state.totalBytes ?: return 0
        if (total <= 0L) return 0
        return ((state.bytesDownloaded * 100L) / total).toInt().coerceIn(0, 100)
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_MODELS, true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun cancelPendingIntent(modelId: String): PendingIntent = PendingIntent.getService(
        this,
        modelId.hashCode(),
        cancelIntent(this, modelId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun createNotificationChannel() {
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Model downloads", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Progress for model files downloaded by ArarAI"
            },
        )
    }

    private val notificationManager get() = getSystemService(NotificationManager::class.java)

    companion object {
        private const val ACTION_DOWNLOAD = "com.jesjobom.ararai.action.DOWNLOAD_MODEL"
        private const val ACTION_CANCEL = "com.jesjobom.ararai.action.CANCEL_MODEL_DOWNLOAD"
        private const val EXTRA_MODEL_ID = "model_id"
        private const val EXTRA_REPLACE = "replace_existing"
        private const val CHANNEL_ID = "model_downloads"
        private const val NOTIFICATION_ID = 1001

        fun downloadIntent(
            context: Context,
            modelId: String,
            replaceExisting: Boolean,
        ): Intent = Intent(context, ModelDownloadService::class.java)
            .setAction(ACTION_DOWNLOAD)
            .putExtra(EXTRA_MODEL_ID, modelId)
            .putExtra(EXTRA_REPLACE, replaceExisting)

        fun cancelIntent(
            context: Context,
            modelId: String,
        ): Intent = Intent(context, ModelDownloadService::class.java)
            .setAction(ACTION_CANCEL)
            .putExtra(EXTRA_MODEL_ID, modelId)
    }
}
