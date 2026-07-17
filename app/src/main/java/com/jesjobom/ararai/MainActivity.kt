package com.jesjobom.ararai

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.jesjobom.ararai.chat.SqliteChatSessionStore
import com.jesjobom.ararai.chat.FileChatMediaRepository
import com.jesjobom.ararai.engine.prepareLiteRtLmCacheDir
import com.jesjobom.ararai.model.ModelConfigLoader
import com.jesjobom.ararai.model.ModelCatalogController
import com.jesjobom.ararai.model.ModelFileDownloader
import com.jesjobom.ararai.model.SharedPreferencesModelSelectionStore
import com.jesjobom.ararai.ui.ArarAiApp
import com.jesjobom.ararai.ui.ArarAiTheme
import com.jesjobom.ararai.ui.androidChatMediaServices

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val modelCatalog = ModelConfigLoader(this, R.raw.fixed_model).loadCatalog()
        val modelController = ModelCatalogController(
            catalog = modelCatalog,
            appFilesRoot = filesDir,
            downloader = ModelFileDownloader(appFilesRoot = filesDir),
            selectionStore = SharedPreferencesModelSelectionStore(this),
        )
        val chatSessionStore = SqliteChatSessionStore(this)
        val chatMediaRepository = FileChatMediaRepository(java.io.File(filesDir, "chat_media"))
        val chatMediaServices = androidChatMediaServices(chatMediaRepository)
        chatMediaRepository.reconcile(chatSessionStore.referencedMediaUris())

        setContent {
            ArarAiTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    ArarAiApp(
                        modelController = modelController,
                        chatSessionStore = chatSessionStore,
                        chatMediaRepository = chatMediaRepository,
                        chatMediaServices = chatMediaServices,
                        systemPrompt = modelCatalog.chat.systemPrompt,
                        appVersionLabel = "v${BuildConfig.VERSION_NAME}",
                        liteRtLmCacheDir = prepareLiteRtLmCacheDir(cacheDir) { error ->
                            Log.w("ArarAI.LiteRtLm", "Unable to prepare LiteRT-LM cache", error)
                        },
                    )
                }
            }
        }
    }
}
