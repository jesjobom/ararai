package com.jesjobom.ararai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.jesjobom.ararai.chat.ChatUiState
import com.jesjobom.ararai.chat.ChatViewModel
import com.jesjobom.ararai.engine.FakeLocalLlmEngine
import com.jesjobom.ararai.model.ModelConfigLoader
import com.jesjobom.ararai.model.ModelDownloadPlanner
import com.jesjobom.ararai.model.ModelDownloadState
import com.jesjobom.ararai.model.ModelResolutionState
import com.jesjobom.ararai.model.ModelResolver
import com.jesjobom.ararai.ui.ArarAiTheme
import com.jesjobom.ararai.ui.ChatScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val modelConfig = ModelConfigLoader(this, R.raw.fixed_model).load()
        val resolution = ModelResolver(filesDir).resolve(modelConfig)
        val downloadState = ModelDownloadPlanner().plan(resolution)
        val localModel = (resolution as? ModelResolutionState.Available)?.model
        val modelStatus = when (downloadState) {
            ModelDownloadState.NotNeeded -> ChatUiState.MODEL_AVAILABLE
            is ModelDownloadState.Needed -> "Download needed: ${downloadState.reason}"
            is ModelDownloadState.Queued -> "Download queued"
            is ModelDownloadState.Downloading -> "Downloading model"
            is ModelDownloadState.Failed -> "Download failed: ${downloadState.message}"
        }

        setContent {
            ArarAiTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val viewModel = remember {
                        ChatViewModel(
                            engine = FakeLocalLlmEngine(),
                            initialModel = localModel,
                            inferenceConfig = modelConfig.inference,
                            initialModelStatus = modelStatus,
                        )
                    }
                    ChatScreen(viewModel = viewModel)
                }
            }
        }
    }
}
