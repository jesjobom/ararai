package com.jesjobom.ararai

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.jesjobom.ararai.chat.DeferredNewChatSessionStore
import com.jesjobom.ararai.chat.FileChatMediaRepository
import com.jesjobom.ararai.chat.SharedPreferencesChatPreferences
import com.jesjobom.ararai.chat.SharedPreferencesInstructionPreferences
import com.jesjobom.ararai.chat.SqliteChatSessionStore
import com.jesjobom.ararai.chat.WhisperCppAudioTranscriber
import com.jesjobom.ararai.engine.prepareLiteRtLmCacheDir
import com.jesjobom.ararai.knowledge.EncryptedWebSearchPreferences
import com.jesjobom.ararai.model.ModelStartupState
import com.jesjobom.ararai.model.SharedPreferencesGenerationPreferences
import com.jesjobom.ararai.settings.SharedPreferencesApplicationExitPreferenceStore
import com.jesjobom.ararai.settings.SharedPreferencesApplicationLanguagePreferenceStore
import com.jesjobom.ararai.settings.SharedPreferencesThemePreferenceStore
import com.jesjobom.ararai.settings.SharedPreferencesTranscriptionLanguagePreferences
import com.jesjobom.ararai.settings.resolveLanguageTag
import com.jesjobom.ararai.ui.AndroidChatTextToSpeechService
import com.jesjobom.ararai.ui.ArarAiApp
import com.jesjobom.ararai.ui.ArarAiTheme
import com.jesjobom.ararai.ui.MlKitChatLanguageIdentifier
import com.jesjobom.ararai.ui.androidChatMediaServices
import com.jesjobom.ararai.voice.SharedPreferencesVoiceChatPreferences
import com.jesjobom.ararai.voice.reconcileVoiceTemporaryFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val openModelManagementRequests = MutableStateFlow(0)

    override fun attachBaseContext(newBase: Context) {
        val language = SharedPreferencesApplicationLanguagePreferenceStore(newBase).language
        super.attachBaseContext(
            SharedPreferencesApplicationLanguagePreferenceStore.localizedContext(newBase, language),
        )
    }

    @Suppress("LongMethod")
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        handleNavigationIntent(intent)

        val app = application as ArarAiApplication
        val modelCatalog = app.modelCatalog
        val modelController = app.modelController
        val chatSessionStore = DeferredNewChatSessionStore(SqliteChatSessionStore(this))
        val chatMediaRepository = FileChatMediaRepository(java.io.File(filesDir, "chat_media"))
        val chatMediaServices = androidChatMediaServices(chatMediaRepository)
        val chatPreferences = SharedPreferencesChatPreferences(this)
        val pendingReportQueue = app.pendingReportQueue
        val reportDeliveryReceiptStore = app.reportDeliveryReceiptStore
        val instructionPreferences = SharedPreferencesInstructionPreferences(this)
        val generationPreferences = SharedPreferencesGenerationPreferences(this)
        val transcriptionLanguagePreferences = SharedPreferencesTranscriptionLanguagePreferences(this)
        val webSearchPreferences = EncryptedWebSearchPreferences(this)
        val audioTranscriber = WhisperCppAudioTranscriber(
            models = { modelController.state.value.models },
            languageTag = {
                transcriptionLanguagePreferences.language.value.resolveLanguageTag(
                    systemLanguageTag = {
                        android.content.res.Resources.getSystem().configuration.locales[0].toLanguageTag()
                    },
                    interfaceLanguageTag = { resources.configuration.locales[0].toLanguageTag() },
                )
            },
        )
        val themePreferenceStore = SharedPreferencesThemePreferenceStore(this)
        val languagePreferenceStore = SharedPreferencesApplicationLanguagePreferenceStore(this)
        val exitPreferenceStore = SharedPreferencesApplicationExitPreferenceStore(this)
        val appliedApplicationLanguage = languagePreferenceStore.language
        val voiceChatPreferences = SharedPreferencesVoiceChatPreferences(this)
        val voiceTemporaryDirectory = java.io.File(cacheDir, "voice_chat")
        reconcileVoiceTemporaryFiles(voiceTemporaryDirectory)
        lifecycleScope.launch(Dispatchers.IO) {
            chatMediaRepository.reconcile(chatSessionStore.referencedMediaUris())
        }

        setContent {
            var selectedApplicationLanguage by remember { mutableStateOf(appliedApplicationLanguage) }
            var shouldConfirmExit by remember { mutableStateOf(exitPreferenceStore.shouldConfirmExit) }
            val themeMode by themePreferenceStore.themeMode.collectAsState()
            val modelState by modelController.state.collectAsState()
            val openModelManagementRequest by openModelManagementRequests.collectAsState()
            val notificationPermissionLauncher =
                rememberLauncherForActivityResult(
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
                        chatPreferences = chatPreferences,
                        pendingReportQueue = pendingReportQueue,
                        reportDeliveryReceiptStore = reportDeliveryReceiptStore,
                        reportDeliveryScheduler = app.reportDeliveryScheduler,
                        instructionPreferences = instructionPreferences,
                        generationPreferences = generationPreferences,
                        transcriptionLanguagePreferences = transcriptionLanguagePreferences,
                        webSearchPreferences = webSearchPreferences,
                        audioTranscriber = audioTranscriber,
                        chatTextToSpeechServiceFactory = { AndroidChatTextToSpeechService(this) },
                        chatLanguageIdentifierFactory = { MlKitChatLanguageIdentifier() },
                        systemPrompt = modelCatalog.chat.systemPrompt,
                        appVersionLabel = "v${BuildConfig.VERSION_NAME}",
                        themeMode = themeMode,
                        onThemeModeChange = themePreferenceStore::setThemeMode,
                        applicationLanguage = selectedApplicationLanguage,
                        appliedApplicationLanguage = appliedApplicationLanguage,
                        onApplicationLanguageChange = { language ->
                            languagePreferenceStore.setLanguage(language)
                            selectedApplicationLanguage = language
                        },
                        onRestartApplication = ::recreate,
                        shouldConfirmExit = shouldConfirmExit,
                        onDisableExitConfirmation = {
                            exitPreferenceStore.disableExitConfirmation()
                            shouldConfirmExit = false
                        },
                        onExitApplication = ::finishAndRemoveTask,
                        voiceChatPreferences = voiceChatPreferences,
                        voiceTemporaryDirectory = voiceTemporaryDirectory,
                        openModelManagementRequest = openModelManagementRequest,
                        liteRtLmCacheDir =
                        prepareLiteRtLmCacheDir(cacheDir) { error ->
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
