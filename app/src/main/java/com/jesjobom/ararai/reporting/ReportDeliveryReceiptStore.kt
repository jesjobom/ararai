package com.jesjobom.ararai.reporting

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class ReportDeliveryReceipt(
    val reportId: String,
    val sentAtEpochMillis: Long,
)

interface ReportDeliveryReceiptStore {
    val latestReceipt: StateFlow<ReportDeliveryReceipt?>
    fun record(reportId: String, sentAtEpochMillis: Long)
}

class SharedPreferencesReportDeliveryReceiptStore(context: Context) : ReportDeliveryReceiptStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val mutableLatestReceipt = MutableStateFlow(loadReceipt())
    override val latestReceipt: StateFlow<ReportDeliveryReceipt?> = mutableLatestReceipt

    override fun record(reportId: String, sentAtEpochMillis: Long) {
        val receipt = ReportDeliveryReceipt(reportId, sentAtEpochMillis)
        preferences.edit()
            .putString(KEY_REPORT_ID, reportId)
            .putLong(KEY_SENT_AT, sentAtEpochMillis)
            .apply()
        mutableLatestReceipt.value = receipt
    }

    private fun loadReceipt(): ReportDeliveryReceipt? = preferences
        .getString(KEY_REPORT_ID, null)
        ?.let { reportId ->
            preferences.getLong(KEY_SENT_AT, -1L)
                .takeIf { it >= 0L }
                ?.let { ReportDeliveryReceipt(reportId, it) }
        }

    private companion object {
        const val PREFERENCES_NAME = "report_delivery_receipt"
        const val KEY_REPORT_ID = "report_id"
        const val KEY_SENT_AT = "sent_at"
    }
}
