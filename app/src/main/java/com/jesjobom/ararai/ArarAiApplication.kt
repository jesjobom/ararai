package com.jesjobom.ararai

import android.app.Application
import androidx.work.WorkManager
import com.google.firebase.FirebaseApp
import com.jesjobom.ararai.model.ForegroundModelDownloadGateway
import com.jesjobom.ararai.model.LegacyModelArtifactMigration
import com.jesjobom.ararai.model.ModelCatalogController
import com.jesjobom.ararai.model.ModelConfigLoader
import com.jesjobom.ararai.model.ModelFileDownloader
import com.jesjobom.ararai.model.SharedPreferencesModelSelectionStore
import com.jesjobom.ararai.reporting.FirebaseAppCheckInstaller
import com.jesjobom.ararai.reporting.FirestoreGeneratedContentReportTransport
import com.jesjobom.ararai.reporting.GeneratedContentReportDelivery
import com.jesjobom.ararai.reporting.ReportDeliveryProvider
import com.jesjobom.ararai.reporting.SharedPreferencesReportDeliveryReceiptStore
import com.jesjobom.ararai.reporting.SqlitePendingReportQueue
import com.jesjobom.ararai.reporting.WorkManagerReportDeliveryScheduler

class ArarAiApplication :
    Application(),
    ReportDeliveryProvider {
    override fun onCreate() {
        super.onCreate()
        if (FirebaseApp.initializeApp(this) != null) FirebaseAppCheckInstaller.install()
        LegacyModelArtifactMigration.run(filesDir)
    }

    val pendingReportQueue by lazy { SqlitePendingReportQueue(this) }

    val reportDeliveryReceiptStore by lazy { SharedPreferencesReportDeliveryReceiptStore(this) }

    val reportDeliveryScheduler by lazy {
        WorkManagerReportDeliveryScheduler(WorkManager.getInstance(this))
    }

    override val reportDelivery by lazy {
        GeneratedContentReportDelivery(
            queue = pendingReportQueue,
            transport = FirestoreGeneratedContentReportTransport(),
            receiptStore = reportDeliveryReceiptStore,
        )
    }

    val modelCatalog by lazy { ModelConfigLoader(this, R.raw.fixed_model).loadCatalog() }

    val modelController: ModelCatalogController by lazy {
        ModelCatalogController(
            catalog = modelCatalog,
            appFilesRoot = filesDir,
            downloader = ModelFileDownloader(appFilesRoot = filesDir),
            selectionStore = SharedPreferencesModelSelectionStore(this),
            downloadGateway = ForegroundModelDownloadGateway(this),
        )
    }
}
