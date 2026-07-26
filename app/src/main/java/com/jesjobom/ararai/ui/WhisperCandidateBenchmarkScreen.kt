package com.jesjobom.ararai.ui

import android.os.Debug
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jesjobom.ararai.chat.DEFAULT_TRANSCRIPTION_THREADS
import com.jesjobom.ararai.model.ManagedModelItem
import com.jesjobom.ararai.model.ModelStartupState
import com.jesjobom.ararai.voice.AndroidVoiceTurnCapture
import com.jesjobom.ararai.voice.VoiceChatSettings
import com.jesjobom.ararai.whisper.WhisperRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.util.Locale

@Composable
@Suppress("LongMethod")
internal fun WhisperCandidateBenchmarkScreen(
    item: ManagedModelItem,
    temporaryDirectory: File,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var capture by remember { mutableStateOf<AndroidVoiceTurnCapture?>(null) }
    var status by remember { mutableStateOf("Ready to record") }
    var report by remember { mutableStateOf<String?>(null) }
    var isBusy by remember { mutableStateOf(false) }
    var benchmarkThreads by remember { mutableStateOf(DEFAULT_TRANSCRIPTION_THREADS) }
    val available = item.state as? ModelStartupState.Available
    val sampleFile = remember(temporaryDirectory) { File(temporaryDirectory, "comparison-sample.wav") }
    var hasSavedSample by remember { mutableStateOf(sampleFile.isFile) }

    fun executeBenchmark(audioFile: File) {
        scope.launch {
            status = "Transcribing..."
            isBusy = true
            try {
                report = runCandidateBenchmark(
                    item = item,
                    modelPath = checkNotNull(available).model.filePath,
                    audioPath = audioFile.absolutePath,
                    threads = benchmarkThreads,
                )
                status = "Completed"
            } catch (error: IllegalStateException) {
                status = "Benchmark failed"
                report = buildString {
                    appendLine("outcome=failure")
                    appendLine("model_id=${item.config.id}")
                    append("message=${error.message ?: "Unknown error"}")
                }
            } finally {
                isBusy = false
                capture?.close()
                capture = null
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { capture?.close() }
    }

    ArarAiScaffold(
        title = "Whisper candidate test",
        subtitle = item.config.name,
        onBack = if (isBusy) null else onBack,
    ) { modifier ->
        Column(
            modifier = modifier
                .verticalScroll(rememberScrollState())
                .padding(vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Record one representative sentence. Recording stops after the configured speech pause.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(status, fontWeight = FontWeight.SemiBold)
            BenchmarkThreadSelector(
                selectedThreads = benchmarkThreads,
                enabled = !isBusy,
                onSelected = { benchmarkThreads = it },
            )
            Button(
                enabled = available != null && !isBusy,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    report = null
                    isBusy = true
                    status = "Listening..."
                    val recorder = AndroidVoiceTurnCapture(
                        context = context,
                        directory = temporaryDirectory,
                        settings = VoiceChatSettings(),
                    )
                    capture = recorder
                    recorder.start(
                        onTurn = { turn ->
                            scope.launch {
                                val audioPath = turn.prompt.uri.removePrefix("file://").removePrefix("file:")
                                try {
                                    sampleFile.parentFile?.mkdirs()
                                    File(audioPath).copyTo(sampleFile, overwrite = true)
                                    hasSavedSample = true
                                    executeBenchmark(sampleFile)
                                } catch (error: IOException) {
                                    status = error.message ?: "Unable to save benchmark audio"
                                    isBusy = false
                                    capture?.close()
                                    capture = null
                                } finally {
                                    File(audioPath).delete()
                                }
                            }
                        },
                        onError = { message ->
                            scope.launch {
                                status = message
                                isBusy = false
                                capture?.close()
                                capture = null
                            }
                        },
                    )
                },
            ) {
                Text("Record benchmark audio")
            }
            if (hasSavedSample) {
                OutlinedButton(
                    enabled = available != null && !isBusy,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        report = null
                        executeBenchmark(sampleFile)
                    },
                ) {
                    Text("Run saved audio with this model")
                }
                OutlinedButton(
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        sampleFile.delete()
                        hasSavedSample = false
                        status = "Saved benchmark audio deleted"
                    },
                ) {
                    Text("Delete saved benchmark audio")
                }
            }
            if (isBusy && status == "Listening...") {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        capture?.cancel()
                        capture = null
                        isBusy = false
                        status = "Recording canceled"
                    },
                ) {
                    Text("Cancel recording")
                }
            }
            report?.let { value ->
                Text(value, style = MaterialTheme.typography.bodySmall)
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { clipboard.setText(AnnotatedString(value)) },
                ) {
                    Text("Copy comparison report")
                }
            }
        }
    }
}

@Composable
private fun BenchmarkThreadSelector(
    selectedThreads: Int,
    enabled: Boolean,
    onSelected: (Int) -> Unit,
) {
    Text("CPU threads", style = MaterialTheme.typography.labelLarge)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BENCHMARK_THREAD_OPTIONS.forEach { threads ->
            OutlinedButton(
                enabled = enabled,
                modifier = Modifier.weight(1f),
                onClick = { onSelected(threads) },
            ) {
                Text(if (selectedThreads == threads) "$threads✓" else threads.toString())
            }
        }
    }
}

private suspend fun runCandidateBenchmark(
    item: ManagedModelItem,
    modelPath: String,
    audioPath: String,
    threads: Int,
): String = coroutineScope {
    val beforePss = currentPssKb()
    var peakPss = beforePss
    val execution = async(Dispatchers.Default) {
        WhisperRuntime.transcribe(
            modelPath = modelPath,
            wavPath = audioPath,
            language = "pt",
            threads = threads,
        )
    }
    while (!execution.isCompleted) {
        peakPss = maxOf(peakPss, currentPssKb())
        delay(MEMORY_SAMPLE_MILLIS)
    }
    val result = execution.await()
    peakPss = maxOf(peakPss, currentPssKb())
    val afterPss = currentPssKb()
    buildString {
        appendLine("outcome=success")
        appendLine("model_id=${item.config.id}")
        appendLine("variant=${item.config.variant.orEmpty()}")
        appendLine("model_bytes=${File(modelPath).length()}")
        appendLine("language=pt")
        appendLine("backend=cpu")
        appendLine("native_optimization=O3,armv8.2-a,dotprod,fp16")
        appendLine("system_info=${WhisperRuntime.systemInfo().replace('\n', ' ').trim()}")
        appendLine("threads=${result.threads}")
        appendLine("audio_duration_ms=${result.audioMillis}")
        appendLine("load_duration_ms=${result.loadMillis}")
        appendLine("transcription_duration_ms=${result.transcriptionMillis}")
        appendLine("real_time_factor=${String.format(Locale.US, "%.3f", result.realTimeFactor)}")
        appendLine("pss_before_kb=$beforePss")
        appendLine("pss_peak_sampled_kb=$peakPss")
        appendLine("pss_after_kb=$afterPss")
        append("transcript=${result.text.trim()}")
    }
}

private fun currentPssKb(): Int = Debug.MemoryInfo().also(Debug::getMemoryInfo).totalPss

private val BENCHMARK_THREAD_OPTIONS = listOf(2, 4, 6, 8)
private const val MEMORY_SAMPLE_MILLIS = 50L
