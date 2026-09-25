package com.jesjobom.ararai

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import androidx.work.WorkManager
import com.google.firebase.FirebaseApp
import com.jesjobom.ararai.chat.SharedPreferencesInstructionPreferences
import com.jesjobom.ararai.engine.AndroidLiteRtLmBridge
import com.jesjobom.ararai.engine.AppLocalLlmRuntime
import com.jesjobom.ararai.engine.LiteRtLmLocalLlmEngine
import com.jesjobom.ararai.engine.LiteRtLmRuntimeTelemetry
import com.jesjobom.ararai.engine.LocalLlmRecoveryGate
import com.jesjobom.ararai.engine.WebSearchKnowledgeToolResolver
import com.jesjobom.ararai.engine.androidLocalLlmRecoveryGate
import com.jesjobom.ararai.engine.prepareLiteRtLmCacheDir
import com.jesjobom.ararai.knowledge.EncryptedWebSearchPreferences
import com.jesjobom.ararai.knowledge.FallbackKnowledgeTool
import com.jesjobom.ararai.knowledge.WikipediaKnowledgeTool
import com.jesjobom.ararai.knowledge.WikipediaOnThisDayKnowledgeTool
import com.jesjobom.ararai.knowledge.WebSearchToolFactory
import com.jesjobom.ararai.math.EvalExLocalMathEngine
import com.jesjobom.ararai.model.ForegroundModelDownloadGateway
import com.jesjobom.ararai.model.LegacyModelArtifactMigration
import com.jesjobom.ararai.model.ModelCatalogController
import com.jesjobom.ararai.model.ModelConfigLoader
import com.jesjobom.ararai.model.ModelFileDownloader
import com.jesjobom.ararai.model.SharedPreferencesModelSelectionStore
import com.jesjobom.ararai.reporting.DiagnosticErrorReportCoordinator
import com.jesjobom.ararai.reporting.FirebaseAppCheckInstaller
import com.jesjobom.ararai.reporting.FirestoreGeneratedContentReportTransport
import com.jesjobom.ararai.reporting.FirestoreRestDiagnosticErrorReportTransport
import com.jesjobom.ararai.reporting.GeneratedContentReportDelivery
import com.jesjobom.ararai.reporting.ReportDeliveryProvider
import com.jesjobom.ararai.reporting.SharedPreferencesReportDeliveryReceiptStore
import com.jesjobom.ararai.reporting.SqlitePendingReportQueue
import com.jesjobom.ararai.reporting.WorkManagerReportDeliveryScheduler
import com.jesjobom.ararai.tools.ApplicationToolCategory
import com.jesjobom.ararai.tools.ApplicationToolDispatcher
import com.jesjobom.ararai.tools.ApplicationToolOperationalState
import com.jesjobom.ararai.tools.defaultApplicationToolRegistry
import com.jesjobom.ararai.tools.wikipediaWidgetApplicationToolRegistry
import com.jesjobom.ararai.ui.ManagedWidgetDraftGenerationResult
import com.jesjobom.ararai.ui.ManagedWidgetDraftUiState
import com.jesjobom.ararai.ui.ManagedWidgetsController
import com.jesjobom.ararai.widget.WidgetToolExecutionGateway
import com.jesjobom.ararai.widget.managed.AndroidWidgetAuthoringDeviceState
import com.jesjobom.ararai.widget.managed.ManagedWidgetApplicationServices
import com.jesjobom.ararai.widget.managed.DispatcherManagedWidgetRepository
import com.jesjobom.ararai.widget.managed.ManagedWidgetExecutionCoordinator
import com.jesjobom.ararai.widget.managed.ManagedWidgetExecutionProvider
import com.jesjobom.ararai.widget.managed.ManagedWidgetManualRefresh
import com.jesjobom.ararai.widget.managed.ManagedWidgetScheduleController
import com.jesjobom.ararai.widget.managed.ManagedWidgetStartupReconciler
import com.jesjobom.ararai.widget.managed.RuntimeManagedWidgetProgramExecutor
import com.jesjobom.ararai.widget.managed.SqliteManagedWidgetRepository
import com.jesjobom.ararai.widget.managed.WidgetAuthoringJobController
import com.jesjobom.ararai.widget.managed.WidgetAuthoringJobFailureReason
import com.jesjobom.ararai.widget.managed.WidgetAuthoringJobOutcome
import com.jesjobom.ararai.widget.managed.WorkManagerManagedWidgetScheduler
import com.jesjobom.ararai.widget.runtime.GatewayWidgetProgramToolExecutor
import com.jesjobom.ararai.widget.runtime.QuickJsWidgetJavaScriptEngine
import com.jesjobom.ararai.widget.runtime.WidgetRuntimeContext
import com.jesjobom.ararai.widget.runtime.WidgetRuntimeCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class ArarAiApplication :
    Application(),
    ReportDeliveryProvider,
    ManagedWidgetExecutionProvider,
    Configuration.Provider {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration = Configuration.Builder().build()

    override fun onCreate() {
        super.onCreate()
        if (FirebaseApp.initializeApp(this) != null) FirebaseAppCheckInstaller.install()
        LegacyModelArtifactMigration.run(filesDir)
        applicationScope.launch(Dispatchers.IO) {
            runCatching { managedWidgetStartupReconciler.reconcile() }
        }
    }

    val pendingReportQueue by lazy { SqlitePendingReportQueue(this) }

    val instructionPreferences by lazy { SharedPreferencesInstructionPreferences(this) }

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

    private val managedWidgetStore by lazy { SqliteManagedWidgetRepository(this) }

    private val managedWidgetRepository by lazy { DispatcherManagedWidgetRepository(managedWidgetStore) }

    val wikipediaTool by lazy { WikipediaKnowledgeTool() }

    val wikipediaOnThisDayTool by lazy { WikipediaOnThisDayKnowledgeTool() }

    private val calculatorEngine by lazy { EvalExLocalMathEngine() }

    val webSearchPreferences by lazy { EncryptedWebSearchPreferences(this) }

    private val webSearchToolFactory by lazy { WebSearchToolFactory() }

    private val webSearchResolver by lazy {
        WebSearchKnowledgeToolResolver {
            webSearchPreferences.settings.value.orderedEnabledProviders
                .map { provider ->
                    webSearchToolFactory.create(provider) {
                        webSearchPreferences.token(provider)
                    }
                }
                .takeIf(List<*>::isNotEmpty)
                ?.let(::FallbackKnowledgeTool)
        }
    }

    val applicationToolRegistry by lazy {
        defaultApplicationToolRegistry(
            instructionPreferences = instructionPreferences,
            webSearchPreferences = webSearchPreferences,
            wikipediaTool = wikipediaTool,
            wikipediaPagesTool = wikipediaTool,
            wikipediaOnThisDayTool = wikipediaOnThisDayTool,
            webSearchTool = webSearchResolver::resolve,
            calculatorEngine = calculatorEngine,
            experimentalWebSearchEnabled = com.jesjobom.ararai.BuildConfig.EXPERIMENTAL_WEB_SEARCH,
        )
    }

    private val applicationToolDispatcher by lazy {
        ApplicationToolDispatcher(applicationToolRegistry)
    }

    private val managedWidgetToolRegistry by lazy {
        wikipediaWidgetApplicationToolRegistry(wikipediaTool, wikipediaOnThisDayTool) {
            ApplicationToolOperationalState(
                enabled = instructionPreferences.settings.value.wikipediaEnabled,
                ready = true,
            )
        }
    }

    val liteRtLmRuntimeTelemetry by lazy {
        LiteRtLmRuntimeTelemetry.android(
            context = this,
            enabled = com.jesjobom.ararai.BuildConfig.DEBUG,
        )
    }

    internal val localLlmRecoveryGate: LocalLlmRecoveryGate by lazy {
        androidLocalLlmRecoveryGate(
            context = this,
            telemetryEnabled = com.jesjobom.ararai.BuildConfig.DEBUG,
        )
    }

    val liteRtLmCacheDir: String? by lazy {
        prepareLiteRtLmCacheDir(cacheDir) { error ->
            Log.w("ArarAI.LiteRtLm", "Unable to prepare LiteRT-LM cache", error)
        }
    }

    val localLlmRuntime: AppLocalLlmRuntime by lazy {
        AppLocalLlmRuntime(
            engineFactory = {
                LiteRtLmLocalLlmEngine(
                    bridge = AndroidLiteRtLmBridge(
                        cacheDir = liteRtLmCacheDir,
                        wikipediaKnowledgeTool = wikipediaTool,
                        webSearchKnowledgeToolResolver = webSearchResolver,
                        calculatorEngine = calculatorEngine,
                        applicationToolDispatcher = applicationToolDispatcher,
                        webSearchDisplayNameProvider = {
                            webSearchPreferences.settings.value.preferredProvider?.displayName
                                ?: "Web search"
                        },
                        runtimeTelemetry = liteRtLmRuntimeTelemetry,
                    ),
                )
            },
        )
    }

    private val managedWidgetScheduler by lazy {
        val networkToolIds = managedWidgetToolRegistry.descriptors()
            .filter { it.category == ApplicationToolCategory.ExternalKnowledge }
            .mapTo(mutableSetOf()) { it.id }
        WorkManagerManagedWidgetScheduler(WorkManager.getInstance(this), networkToolIds)
    }

    private val managedWidgetCoordinator by lazy {
        val runtime = WidgetRuntimeCoordinator(
            QuickJsWidgetJavaScriptEngine(),
            GatewayWidgetProgramToolExecutor(
                WidgetToolExecutionGateway(ApplicationToolDispatcher(managedWidgetToolRegistry)),
            ),
        )
        ManagedWidgetExecutionCoordinator(
            repository = managedWidgetRepository,
            executor = RuntimeManagedWidgetProgramExecutor(runtime),
            contextProvider = ::currentWidgetRuntimeContext,
        )
    }

    private val managedWidgetStartupReconciler by lazy {
        ManagedWidgetStartupReconciler(managedWidgetRepository, managedWidgetScheduler)
    }

    internal val managedWidgetApplicationServices by lazy {
        ManagedWidgetApplicationServices(
            repository = managedWidgetRepository,
            schedules = ManagedWidgetScheduleController(managedWidgetRepository, managedWidgetScheduler),
            manualRefresh = ManagedWidgetManualRefresh(managedWidgetCoordinator),
            toolRegistry = managedWidgetToolRegistry,
        )
    }

    internal val managedWidgetsController: ManagedWidgetsController by lazy {
        ManagedWidgetsController(
            services = managedWidgetApplicationServices,
            localLlmEngine = localLlmRuntime.engine,
            recoveryGate = localLlmRecoveryGate,
        )
    }

    internal val widgetAuthoringNotificationPresenter by lazy {
        WidgetAuthoringNotificationPresenter(this)
    }

    internal val widgetAuthoringJobs: WidgetAuthoringJobController<ManagedWidgetDraftUiState> by lazy {
        WidgetAuthoringJobController(
            scope = applicationScope,
            executor = { request, onProgress ->
                when (
                    val result = managedWidgetsController.generateDraft(
                        request.model,
                        request.inference,
                        request.instruction,
                        request.widgetId,
                        onProgress,
                    )
                ) {
                    is ManagedWidgetDraftGenerationResult.Ready ->
                        WidgetAuthoringJobOutcome.Ready(result.value)
                    ManagedWidgetDraftGenerationResult.ModelUnavailable ->
                        WidgetAuthoringJobOutcome.Failure(WidgetAuthoringJobFailureReason.ModelUnavailable)
                    ManagedWidgetDraftGenerationResult.ModelLoadFailed ->
                        WidgetAuthoringJobOutcome.Failure(WidgetAuthoringJobFailureReason.ModelLoadFailed)
                    ManagedWidgetDraftGenerationResult.MissingWidget ->
                        WidgetAuthoringJobOutcome.Failure(WidgetAuthoringJobFailureReason.MissingWidget)
                    is ManagedWidgetDraftGenerationResult.Unachievable ->
                        WidgetAuthoringJobOutcome.Failure(
                            WidgetAuthoringJobFailureReason.Unachievable,
                            result.reason,
                        )
                    is ManagedWidgetDraftGenerationResult.NeedsClarification ->
                        WidgetAuthoringJobOutcome.Failure(
                            WidgetAuthoringJobFailureReason.NeedsClarification,
                            result.question,
                        )
                    is ManagedWidgetDraftGenerationResult.StageFailed ->
                        WidgetAuthoringJobOutcome.Failure(
                            WidgetAuthoringJobFailureReason.StageFailed,
                            "${result.stage.name}|${result.code.name}",
                        )
                    ManagedWidgetDraftGenerationResult.GenerationTimedOut ->
                        WidgetAuthoringJobOutcome.Failure(WidgetAuthoringJobFailureReason.GenerationTimedOut)
                }
            },
            deviceState = AndroidWidgetAuthoringDeviceState(this),
            presenter = widgetAuthoringNotificationPresenter,
        )
    }

    override suspend fun executeManagedWidget(widgetId: String) {
        managedWidgetCoordinator.execute(widgetId)
    }

    val diagnosticErrorReportCoordinator by lazy {
        DiagnosticErrorReportCoordinator(FirestoreRestDiagnosticErrorReportTransport())
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

    private fun currentWidgetRuntimeContext(): WidgetRuntimeContext {
        val now = ZonedDateTime.now()
        return WidgetRuntimeContext(
            locale = Locale.getDefault().toLanguageTag(),
            timezone = now.zone.id,
            localTime = now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            seed = System.currentTimeMillis(),
        )
    }
}
