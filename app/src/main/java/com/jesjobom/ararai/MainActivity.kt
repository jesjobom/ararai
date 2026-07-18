package com.jesjobom.ararai

import android.Manifest
import android.os.Bundle
import android.os.Build
import android.content.pm.PackageManager
import android.content.Intent
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.jesjobom.ararai.chat.SqliteChatSessionStore
import com.jesjobom.ararai.chat.FileChatMediaRepository
import com.jesjobom.ararai.engine.prepareLiteRtLmCacheDir
import com.jesjobom.ararai.model.ModelStartupState
import com.jesjobom.ararai.settings.SharedPreferencesThemePreferenceStore
import com.jesjobom.ararai.ui.ArarAiApp
import com.jesjobom.ararai.ui.ArarAiTheme
import com.jesjobom.ararai.ui.AndroidChatTextToSpeechService
import com.jesjobom.ararai.ui.androidChatMediaServices
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private val openModelManagementRequests = MutableStateFlow(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleNavigationIntent(intent)

        val app = application as ArarAiApplication
        val modelCatalog = app.modelCatalog
        val modelController = app.modelController
        val chatSessionStore = SqliteChatSessionStore(this)
        val chatMediaRepository = FileChatMediaRepository(java.io.File(filesDir, "chat_media"))
        val chatMediaServices = androidChatMediaServices(chatMediaRepository)
        val themePreferenceStore = SharedPreferencesThemePreferenceStore(this)
        chatMediaRepository.reconcile(chatSessionStore.referencedMediaUris())

        setContent {
            val themeMode by themePreferenceStore.themeMode.collectAsState()
            val modelState by modelController.state.collectAsState()
            val openModelManagementRequest by openModelManagementRequests.collectAsState()
            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { }
            val isDownloading = modelState.models.any { it.state is ModelStartupState.Downloading }
            LaunchedEffect(isDownloading) {
                if (
                    isDownloading &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            ArarAiTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    ArarAiApp(
                        modelController = modelController,
                        chatSessionStore = chatSessionStore,
                        chatMediaRepository = chatMediaRepository,
                        chatMediaServices = chatMediaServices,
                        chatTextToSpeechServiceFactory = { AndroidChatTextToSpeechService(this) },
                        systemPrompt = modelCatalog.chat.systemPrompt,
                        appVersionLabel = "v${BuildConfig.VERSION_NAME}",
                        themeMode = themeMode,
                        onThemeModeChange = themePreferenceStore::setThemeMode,
                        openModelManagementRequest = openModelManagementRequest,
                        liteRtLmCacheDir = prepareLiteRtLmCacheDir(cacheDir) { error ->
                            Log.w("ArarAI.LiteRtLm", "Unable to prepare LiteRT-LM cache", error)
                        },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNavigationIntent(intent)
    }

    private fun handleNavigationIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_MODELS, false) == true) {
            openModelManagementRequests.value += 1
            intent.removeExtra(EXTRA_OPEN_MODELS)
        }
    }

    companion object {
        const val EXTRA_OPEN_MODELS = "open_model_management"
    }
}
