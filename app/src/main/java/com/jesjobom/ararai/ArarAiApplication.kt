package com.jesjobom.ararai

import android.app.Application
import com.jesjobom.ararai.model.ForegroundModelDownloadGateway
import com.jesjobom.ararai.model.LegacyModelArtifactMigration
import com.jesjobom.ararai.model.ModelCatalogController
import com.jesjobom.ararai.model.ModelConfigLoader
import com.jesjobom.ararai.model.ModelFileDownloader
import com.jesjobom.ararai.model.SharedPreferencesModelSelectionStore

class ArarAiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        LegacyModelArtifactMigration.run(filesDir)
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
