package com.jesjobom.ararai.benchmark

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jesjobom.ararai.BuildConfig
import com.jesjobom.ararai.engine.AndroidLiteRtLmBridge
import com.jesjobom.ararai.engine.LiteRtLmLocalLlmEngine
import com.jesjobom.ararai.engine.ToolCallingLog
import com.jesjobom.ararai.engine.prepareLiteRtLmCacheDir
import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.LocalModel
import com.jesjobom.ararai.model.ModelAccelerationPolicy
import com.jesjobom.ararai.ui.ArarAiTheme

/**
 * Runs one tool-calling case in a dedicated process. LiteRT-LM currently blocks while disposing
 * tool-enabled conversations, so process death is the only reliable native-resource boundary.
 */
class ToolCallingDiagnosticActivity : ComponentActivity() {
    @Suppress("LongMethod", "TooGenericExceptionCaught")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val request = DiagnosticRequest.from(intent)
        if (request == null) {
            finish()
            return
        }

        setContent {
            var status by remember { mutableStateOf("Loading ${request.model.name}") }
            var reportText by remember { mutableStateOf<String?>(null) }
            var diagnostic by remember { mutableStateOf("case=${request.caseId}\nphase=load") }
            val close = {
                finish()
            }
            BackHandler(onBack = close)

            LaunchedEffect(request) {
                val engine =
                    LiteRtLmLocalLlmEngine(
                        AndroidLiteRtLmBridge(
                            cacheDir = prepareLiteRtLmCacheDir(cacheDir),
                        ),
                    )
                try {
                    ToolCallingLog.info(
                        "isolated diagnostic process pid=${Process.myPid()} case=${request.caseId} " +
                            "litertLmVersion=${BuildConfig.LITERT_LM_VERSION}",
                    )
                    engine.load(request.model, request.inference)
                    status = "Running ${request.caseId}"
                    diagnostic += "\nphase=matrix"
                    val case = defaultToolCallingCases().single { it.id == request.caseId }
                    val report =
                        ToolCallingCharacterizationRunner(engine, listOf(case)).run(
                            modelId = request.model.id,
                            modelSha256 = request.sha256,
                            repetitions = 1,
                        ) { progress ->
                            val event =
                                "case=${progress.caseId} phase=${progress.phase.name.lowercase()}" +
                                    (progress.detail?.let { " detail=$it" } ?: "")
                            ToolCallingLog.info(event)
                            diagnostic += "\n$event"
                        }
                    reportText = report.asText()
                    status = "Case complete — close to release native resources"
                } catch (error: Throwable) {
                    ToolCallingLog.error("isolated characterization failed", error)
                    diagnostic +=
                        "\nphase=failed\nexceptionType=${error::class.qualifiedName}" +
                        "\nexceptionMessage=${error.message.orEmpty()}"
                    status = "Case failed"
                }
                // Deliberately do not call engine.unload(): it is the native call under
                // characterization. Closing this Activity kills only this diagnostic process.
            }

            ArarAiTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier =
                        Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text("Isolated tool-calling diagnostic", style = MaterialTheme.typography.headlineSmall)
                        Text(status)
                        if (reportText == null && !status.endsWith("failed")) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        SelectionContainer {
                            Text(reportText ?: diagnostic, style = MaterialTheme.typography.bodySmall)
                        }
                        reportText?.let { report ->
                            Button(
                                onClick = { shareReport(report) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Share report")
                            }
                        }
                        Button(onClick = close, modifier = Modifier.fillMaxWidth()) {
                            Text(if (reportText == null) "Cancel and release process" else "Close and release process")
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Process.killProcess(Process.myPid())
    }

    private fun shareReport(report: String) {
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "ArarAI tool-calling characterization")
                    putExtra(Intent.EXTRA_TEXT, report)
                },
                "Share characterization report",
            ),
        )
    }

    companion object {
        fun intent(
            context: Context,
            model: LocalModel,
            inference: InferenceConfig,
            sha256: String,
            caseId: String,
        ): Intent = Intent(context, ToolCallingDiagnosticActivity::class.java).apply {
            putExtra(EXTRA_MODEL_ID, model.id)
            putExtra(EXTRA_MODEL_NAME, model.name)
            putExtra(EXTRA_MODEL_PATH, model.filePath)
            putExtra(EXTRA_ACCELERATION, model.acceleration.name)
            putExtra(EXTRA_CONTEXT_TOKENS, inference.contextTokens)
            putExtra(EXTRA_MAX_TOKENS, inference.maxTokens)
            putExtra(EXTRA_TEMPERATURE, inference.temperature)
            putExtra(EXTRA_TOP_P, inference.topP)
            putExtra(EXTRA_TOP_K, inference.topK)
            putExtra(EXTRA_MIN_P, inference.minP)
            putExtra(EXTRA_REPEAT_PENALTY, inference.repeatPenalty)
            putExtra(EXTRA_SHA256, sha256)
            putExtra(EXTRA_CASE_ID, caseId)
        }

        private const val EXTRA_MODEL_ID = "model_id"
        private const val EXTRA_MODEL_NAME = "model_name"
        private const val EXTRA_MODEL_PATH = "model_path"
        private const val EXTRA_ACCELERATION = "acceleration"
        private const val EXTRA_CONTEXT_TOKENS = "context_tokens"
        private const val EXTRA_MAX_TOKENS = "max_tokens"
        private const val EXTRA_TEMPERATURE = "temperature"
        private const val EXTRA_TOP_P = "top_p"
        private const val EXTRA_TOP_K = "top_k"
        private const val EXTRA_MIN_P = "min_p"
        private const val EXTRA_REPEAT_PENALTY = "repeat_penalty"
        private const val EXTRA_SHA256 = "sha256"
        private const val EXTRA_CASE_ID = "case_id"
    }

    private data class DiagnosticRequest(
        val model: LocalModel,
        val inference: InferenceConfig,
        val sha256: String,
        val caseId: String,
    ) {
        companion object {
            @Suppress("ReturnCount")
            fun from(intent: Intent): DiagnosticRequest? {
                val id = intent.getStringExtra(EXTRA_MODEL_ID) ?: return null
                val name = intent.getStringExtra(EXTRA_MODEL_NAME) ?: return null
                val path = intent.getStringExtra(EXTRA_MODEL_PATH) ?: return null
                val acceleration =
                    intent.getStringExtra(EXTRA_ACCELERATION)?.let(ModelAccelerationPolicy::valueOf)
                        ?: return null
                val sha256 = intent.getStringExtra(EXTRA_SHA256) ?: return null
                val caseId = intent.getStringExtra(EXTRA_CASE_ID) ?: return null
                return DiagnosticRequest(
                    model = LocalModel(id = id, name = name, filePath = path, acceleration = acceleration),
                    inference =
                    InferenceConfig(
                        contextTokens = intent.getIntExtra(EXTRA_CONTEXT_TOKENS, 2048),
                        maxTokens = intent.getIntExtra(EXTRA_MAX_TOKENS, 128),
                        temperature = intent.getFloatExtra(EXTRA_TEMPERATURE, 0.2f),
                        topP = intent.getFloatExtra(EXTRA_TOP_P, 0.9f),
                        topK = intent.getIntExtra(EXTRA_TOP_K, 40),
                        minP = intent.getFloatExtra(EXTRA_MIN_P, 0.05f),
                        repeatPenalty = intent.getFloatExtra(EXTRA_REPEAT_PENALTY, 1.1f),
                    ),
                    sha256 = sha256,
                    caseId = caseId,
                )
            }
        }
    }
}
