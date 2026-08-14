package com.jesjobom.ararai.reporting

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

interface ReportDeliveryProvider {
    val reportDelivery: GeneratedContentReportDelivery?
}

class ReportDeliveryWorker(
    appContext: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(appContext, parameters) {
    @Suppress("ReturnCount")
    override suspend fun doWork(): Result {
        val reportId = inputData.getString(KEY_REPORT_ID) ?: return Result.failure()
        val provider = applicationContext as? ReportDeliveryProvider ?: return Result.failure()
        val delivery = provider.reportDelivery ?: return Result.failure()
        return when (delivery.deliver(reportId)) {
            ReportDeliveryOutcome.Sent, ReportDeliveryOutcome.Missing -> Result.success()
            ReportDeliveryOutcome.Retry -> Result.retry()
            ReportDeliveryOutcome.Failed -> Result.failure()
        }
    }

    companion object {
        const val KEY_REPORT_ID = "report_id"
    }
}

class WorkManagerReportDeliveryScheduler(
    private val workManager: WorkManager,
) : ReportDeliveryScheduler {
    override fun schedule(reportId: String) {
        val request = OneTimeWorkRequestBuilder<ReportDeliveryWorker>()
            .setInputData(Data.Builder().putString(ReportDeliveryWorker.KEY_REPORT_ID, reportId).build())
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.EXPONENTIAL,
                MIN_BACKOFF_MINUTES,
                TimeUnit.MINUTES,
            )
            .build()
        workManager.enqueueUniqueWork(
            "generated-content-report-$reportId",
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    private companion object {
        const val MIN_BACKOFF_MINUTES = 10L
    }
}

fun interface ReportDeliveryScheduler {
    fun schedule(reportId: String)
}
