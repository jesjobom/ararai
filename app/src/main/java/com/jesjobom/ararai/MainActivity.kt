package com.jesjobom.ararai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.jesjobom.ararai.model.ModelConfigLoader
import com.jesjobom.ararai.model.ModelFileDownloader
import com.jesjobom.ararai.model.ModelStartupController
import com.jesjobom.ararai.ui.ArarAiApp
import com.jesjobom.ararai.ui.ArarAiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val modelConfig = ModelConfigLoader(this, R.raw.fixed_model).load()
        val startupController = ModelStartupController(
            config = modelConfig,
            appFilesRoot = filesDir,
            downloader = ModelFileDownloader(appFilesRoot = filesDir),
        )

        setContent {
            ArarAiTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    ArarAiApp(
                        modelConfig = modelConfig,
                        startupController = startupController,
                    )
                }
            }
        }
    }
}
