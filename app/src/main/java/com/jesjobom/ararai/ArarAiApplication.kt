package com.jesjobom.ararai

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager
import com.google.firebase.FirebaseApp
import com.jesjobom.ararai.chat.SharedPreferencesInstructionPreferences
import com.jesjobom.ararai.knowledge.WikipediaKnowledgeTool
import com.jesjobom.ararai.knowledge.WikipediaOnThisDayKnowledgeTool
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
import com.jesjobom.ararai.tools.wikipediaWidgetApplicationToolRegistry
import com.jesjobom.ararai.widget.WidgetToolExecutionGateway
import com.jesjobom.ararai.widget.managed.DispatcherManagedWidgetRepository
import com.jesjobom.ararai.widget.managed.ManagedWidgetApplicationServices
import com.jesjobom.ararai.widget.managed.ManagedWidgetExecutionCoordinator
import com.jesjobom.ararai.widget.managed.ManagedWidgetExecutionProvider
import com.jesjobom.ararai.widget.managed.ManagedWidgetManualRefresh
import com.jesjobom.ararai.widget.managed.ManagedWidgetScheduleController
import com.jesjobom.ararai.widget.managed.ManagedWidgetStartupReconciler
import com.jesjobom.ararai.widget.managed.RuntimeManagedWidgetProgramExecutor
import com.jesjobom.ararai.widget.managed.SqliteManagedWidgetRepository
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

    private val managedWidgetToolRegistry by lazy {
        val pagesTool = WikipediaKnowledgeTool()
        val onThisDayTool = WikipediaOnThisDayKnowledgeTool()
        wikipediaWidgetApplicationToolRegistry(pagesTool, onThisDayTool) {
            ApplicationToolOperationalState(
                enabled = instructionPreferences.settings.value.wikipediaEnabled,
                ready = true,
            )
        }
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
