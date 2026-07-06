package com.jesjobom.ararai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.jesjobom.ararai.chat.ChatViewModel
import com.jesjobom.ararai.engine.FakeLocalLlmEngine
import com.jesjobom.ararai.model.ModelConfigLoader
import com.jesjobom.ararai.model.ModelFileDownloader
import com.jesjobom.ararai.model.ModelStartupController
import com.jesjobom.ararai.ui.ArarAiTheme
import com.jesjobom.ararai.ui.ChatScreen

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
                    val viewModel = remember {
                        ChatViewModel(
                            engine = FakeLocalLlmEngine(),
                            initialModel = null,
                            inferenceConfig = modelConfig.inference,
                        )
                    }
                    LaunchedEffect(startupController, viewModel) {
                        startupController.state.collect(viewModel::onModelStartupState)
                    }
                    ChatScreen(
                        viewModel = viewModel,
                        onRetryModelDownload = startupController::retry,
                    )
                }
            }
        }
    }
}
