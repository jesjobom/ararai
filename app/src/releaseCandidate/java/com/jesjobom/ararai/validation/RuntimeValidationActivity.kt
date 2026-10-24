package com.jesjobom.ararai.validation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jesjobom.ararai.R
import com.jesjobom.ararai.ui.ArarAiScaffold
import com.jesjobom.ararai.ui.ArarAiTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RuntimeValidationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ArarAiTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    RuntimeValidationScreen(
                        runner = remember { RuntimeValidationRunner(applicationContext) },
                        onKeepScreenOn = ::keepScreenOn,
                        onCopy = ::copyReport,
                        onShare = ::shareReport,
                        onClose = ::finish,
                    )
                }
            }
        }
    }

    private fun keepScreenOn(enabled: Boolean) {
        if (enabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun copyReport(report: String) {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(
            ClipData.newPlainText("ArarAI runtime validation", report),
        )
        Toast.makeText(this, R.string.runtime_validation_copied, Toast.LENGTH_SHORT).show()
    }

    private fun shareReport(report: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.runtime_validation_share_subject))
            putExtra(Intent.EXTRA_TEXT, report)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.runtime_validation_share)))
    }
}

private sealed interface RuntimeValidationScreenState {
    data object Ready : RuntimeValidationScreenState
    data object Running : RuntimeValidationScreenState
    data class Completed(val report: RuntimeValidationReport) : RuntimeValidationScreenState
    data object Failed : RuntimeValidationScreenState
}

@Composable
@Suppress("LongMethod")
private fun RuntimeValidationScreen(
    runner: RuntimeValidationRunner,
    onKeepScreenOn: (Boolean) -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var state: RuntimeValidationScreenState by remember { mutableStateOf(RuntimeValidationScreenState.Ready) }

    fun startValidation() {
        if (state == RuntimeValidationScreenState.Running) return
        state = RuntimeValidationScreenState.Running
        onKeepScreenOn(true)
        scope.launch {
            state = try {
                RuntimeValidationScreenState.Completed(
                    withContext(Dispatchers.Default) { runner.run() },
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                RuntimeValidationScreenState.Failed
            } finally {
                onKeepScreenOn(false)
            }
        }
    }

    ArarAiScaffold(
        title = stringResource(R.string.runtime_validation_title),
        subtitle = stringResource(R.string.runtime_validation_subtitle),
        onBack = if (state == RuntimeValidationScreenState.Running) null else onClose,
    ) { modifier ->
        Column(
            modifier = modifier
                .verticalScroll(rememberScrollState())
                .padding(vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.runtime_validation_description),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.runtime_validation_privacy),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            when (val current = state) {
                RuntimeValidationScreenState.Ready -> ValidationReady(::startValidation)
                RuntimeValidationScreenState.Running -> ValidationRunning()
                RuntimeValidationScreenState.Failed -> ValidationFailed(::startValidation)
                is RuntimeValidationScreenState.Completed -> ValidationCompleted(
                    report = current.report,
                    onRunAgain = ::startValidation,
                    onCopy = onCopy,
                    onShare = onShare,
                )
            }
        }
    }
}

@Composable
private fun ValidationReady(onRun: () -> Unit) {
    Text(
        text = stringResource(R.string.runtime_validation_ready),
        fontWeight = FontWeight.SemiBold,
    )
    Button(onClick = onRun, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.runtime_validation_run))
    }
}

@Composable
private fun ValidationRunning() {
    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    Text(
        text = stringResource(R.string.runtime_validation_running),
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        text = stringResource(R.string.runtime_validation_running_note),
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun ValidationFailed(onRunAgain: () -> Unit) {
    Text(
        text = stringResource(R.string.runtime_validation_internal_failure),
        color = MaterialTheme.colorScheme.error,
        fontWeight = FontWeight.SemiBold,
    )
    OutlinedButton(onClick = onRunAgain, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.runtime_validation_run_again))
    }
}

@Composable
private fun ValidationCompleted(
    report: RuntimeValidationReport,
    onRunAgain: () -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
) {
    val encoded = remember(report) { report.toCanonicalJson() }
    Text(
        text = if (report.overallPassed) {
            stringResource(R.string.runtime_validation_passed, report.passedCount)
        } else {
            stringResource(R.string.runtime_validation_failed, report.passedCount, report.failedCount)
        },
        color = if (report.overallPassed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.titleMedium,
    )
    Text(
        text = stringResource(
            R.string.runtime_validation_environment,
            report.environment.manufacturer,
            report.environment.model,
            report.environment.androidRelease,
            report.environment.sdkInt,
            report.environment.buildType,
        ),
        style = MaterialTheme.typography.bodySmall,
    )
    HorizontalDivider()
    report.cases.forEach { result ->
        ValidationCaseRow(result)
    }
    HorizontalDivider()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(onClick = { onCopy(encoded) }, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.runtime_validation_copy))
        }
        Button(onClick = { onShare(encoded) }, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.runtime_validation_share))
        }
    }
    OutlinedButton(onClick = onRunAgain, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.runtime_validation_run_again))
    }
}

@Composable
private fun ValidationCaseRow(result: RuntimeValidationCaseResult) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = if (result.passed) {
                stringResource(R.string.runtime_validation_case_pass, result.id)
            } else {
                stringResource(R.string.runtime_validation_case_fail, result.id, result.outcome)
            },
            color = if (result.passed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(
                R.string.runtime_validation_case_metrics,
                result.durationMillis,
                result.pssBeforeKb,
                result.pssPeakKb,
                result.pssAfterKb,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
