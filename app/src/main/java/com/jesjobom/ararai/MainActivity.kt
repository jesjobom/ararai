package com.jesjobom.ararai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.jesjobom.ararai.chat.SqliteChatSessionStore
import com.jesjobom.ararai.model.ModelConfigLoader
import com.jesjobom.ararai.model.ModelCatalogController
import com.jesjobom.ararai.model.ModelFileDownloader
import com.jesjobom.ararai.model.SharedPreferencesModelSelectionStore
import com.jesjobom.ararai.ui.ArarAiApp
import com.jesjobom.ararai.ui.ArarAiTheme

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

        setContent {
            ArarAiTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    ArarAiApp(
                        modelController = modelController,
                        chatSessionStore = chatSessionStore,
                        systemPrompt = modelCatalog.chat.systemPrompt,
                        appVersionLabel = "v${BuildConfig.VERSION_NAME}",
                    )
                }
            }
        }
    }
}
