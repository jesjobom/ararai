package com.jesjobom.ararai.widget.managed

import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.LocalModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.min

/** A requested widget authoring generation submitted to the background job controller. */
internal data class WidgetAuthoringJobRequest(
    val model: LocalModel,
    val inference: InferenceConfig,
    val instruction: String,
    val widgetId: String?,
    val probe: Boolean = false,
    val requestId: String = UUID.randomUUID().toString(),
    val submittedAtMillis: Long = System.currentTimeMillis(),
)

internal enum class WidgetAuthoringDeferralReason {
    ThermalState,
    LowMemory,
}

internal enum class WidgetAuthoringJobFailureReason {
    DeferredBudgetExhausted,
    ModelUnavailable,
    ModelLoadFailed,
    MissingWidget,
    Unachievable,
    NeedsClarification,
    StageFailed,
    GenerationTimedOut,
    Unexpected,
}

/** Result of a device-state evaluation performed before starting a generation. */
internal data class WidgetAuthoringDeviceEvaluation(
    val acceptable: Boolean,
    val deferralReason: WidgetAuthoringDeferralReason? = null,
) {
    init {
        require(acceptable xor (deferralReason != null))
    }

    companion object {
        val Acceptable = WidgetAuthoringDeviceEvaluation(acceptable = true)
    }
}

internal interface WidgetAuthoringDeviceState {
    fun evaluate(): WidgetAuthoringDeviceEvaluation
}

internal sealed interface WidgetAuthoringJobOutcome<out T> {
    data class Ready<T>(val value: T) : WidgetAuthoringJobOutcome<T>

    data class Failure(
        val reason: WidgetAuthoringJobFailureReason,
        val detail: String? = null,
    ) : WidgetAuthoringJobOutcome<Nothing>
}

internal sealed interface WidgetAuthoringJobState {
    val request: WidgetAuthoringJobRequest

    data class Queued(override val request: WidgetAuthoringJobRequest) : WidgetAuthoringJobState

    data class Running(
        override val request: WidgetAuthoringJobRequest,
        val progress: WidgetAuthoringProgress?,
    ) : WidgetAuthoringJobState

    data class Deferred(
        override val request: WidgetAuthoringJobRequest,
        val reason: WidgetAuthoringDeferralReason,
        val attempt: Int,
        val nextAttemptAtMillis: Long,
    ) : WidgetAuthoringJobState

    data class Succeeded<T>(
        override val request: WidgetAuthoringJobRequest,
        val value: T,
    ) : WidgetAuthoringJobState

    data class Failed(
        override val request: WidgetAuthoringJobRequest,
        val reason: WidgetAuthoringJobFailureReason,
        val detail: String? = null,
    ) : WidgetAuthoringJobState

    data class Cancelled(override val request: WidgetAuthoringJobRequest) : WidgetAuthoringJobState
}

/**
 * Bounded deferral budget for jobs that cannot start because the device is not
 * in an acceptable state (thermal or memory pressure).
 */
internal data class WidgetAuthoringDeferralPolicy(
    val maximumAttempts: Int = MAXIMUM_ATTEMPTS,
    val baseBackoffMillis: Long = BASE_BACKOFF_MILLIS,
    val maximumBackoffMillis: Long = MAXIMUM_BACKOFF_MILLIS,
) {
    init {
        require(maximumAttempts >= 1)
        require(baseBackoffMillis > 0)
        require(maximumBackoffMillis >= baseBackoffMillis)
    }

    fun backoffMillis(attempt: Int): Long {
        val shift = (attempt - 1).coerceAtLeast(0).coerceAtMost(MAX_BACKOFF_SHIFT)
        return (baseBackoffMillis shl shift).coerceAtMost(maximumBackoffMillis)
    }

    companion object {
        const val MAXIMUM_ATTEMPTS = 6
        const val BASE_BACKOFF_MILLIS = 60_000L
        const val MAXIMUM_BACKOFF_MILLIS = 15 * 60_000L
        private const val MAX_BACKOFF_SHIFT = 4
    }
}

internal interface WidgetAuthoringJobPresenter {
    fun onStateChanged(state: WidgetAuthoringJobState)
}

/**
 * Application-scoped, single-flight coordinator for background widget authoring
 * jobs. At most one job may be queued, running, or deferred at a time; a new
 * request while a job is active is refused with [WidgetAuthoringJobController
 * .SubmitResult.BusyWithActiveJob].
 *
 * The controller owns job state transitions, cooperative cancellation, bounded
 * device-state deferral, and progress propagation. It does not interpret the
 * generation result; the executor maps the pipeline outcome into
 * [WidgetAuthoringJobOutcome].
 */
internal class WidgetAuthoringJobController<T>(
    private val scope: CoroutineScope,
    private val executor: suspend (
        request: WidgetAuthoringJobRequest,
        onProgress: (WidgetAuthoringProgress) -> Unit,
    ) -> WidgetAuthoringJobOutcome<T>,
    private val deviceState: WidgetAuthoringDeviceState,
    private val presenter: WidgetAuthoringJobPresenter? = null,
    private val deferral: WidgetAuthoringDeferralPolicy = WidgetAuthoringDeferralPolicy(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    sealed interface SubmitResult {
        data class Accepted(val requestId: String) : SubmitResult

        data object BusyWithActiveJob : SubmitResult
    }

    private val mutableState = MutableStateFlow<WidgetAuthoringJobState?>(null)
    val state: StateFlow<WidgetAuthoringJobState?> = mutableState.asStateFlow()

    private var job: Job? = null
    private var activeRequest: WidgetAuthoringJobRequest? = null

    val active: Boolean
        get() = mutableState.value.let { current ->
            current is WidgetAuthoringJobState.Queued ||
                current is WidgetAuthoringJobState.Running ||
                current is WidgetAuthoringJobState.Deferred
        }

    @Synchronized
    fun submit(request: WidgetAuthoringJobRequest): SubmitResult {
        if (active) return SubmitResult.BusyWithActiveJob
        transition(WidgetAuthoringJobState.Queued(request))
        activeRequest = request
        job = scope.launch { run(request) }
        return SubmitResult.Accepted(request.requestId)
    }

    /**
     * Cancels the active job, if any. Returns true when a job was cancelled and
     * a new request may be submitted.
     */
    @Synchronized
    fun cancel(): Boolean {
        val current = activeRequest ?: return false
        job?.cancel()
        job = null
        activeRequest = null
        transition(WidgetAuthoringJobState.Cancelled(current))
        return true
    }

    /** Clears a terminal job state (succeeded, failed, cancelled) so the UI can start fresh. */
    @Synchronized
    fun clear() {
        val current = mutableState.value ?: return
        if (current is WidgetAuthoringJobState.Queued ||
            current is WidgetAuthoringJobState.Running ||
            current is WidgetAuthoringJobState.Deferred
        ) {
            return
        }
        mutableState.value = null
        presenter?.onStateChanged(current)
    }

    private suspend fun run(request: WidgetAuthoringJobRequest) {
        try {
            var attempt = 0
            while (true) {
                attempt += 1
                val evaluation = deviceState.evaluate()
                if (!evaluation.acceptable) {
                    val reason = evaluation.deferralReason
                        ?: WidgetAuthoringDeferralReason.LowMemory
                    if (attempt >= deferral.maximumAttempts) {
                        transition(
                            WidgetAuthoringJobState.Failed(request, WidgetAuthoringJobFailureReason.DeferredBudgetExhausted),
                        )
                        return
                    }
                    val backoff = deferral.backoffMillis(attempt)
                    transition(
                        WidgetAuthoringJobState.Deferred(
                            request = request,
                            reason = reason,
                            attempt = attempt,
                            nextAttemptAtMillis = clock() + backoff,
                        ),
                    )
                    delay(backoff)
                    continue
                }
                transition(WidgetAuthoringJobState.Running(request, progress = null))
                val outcome = try {
                    executor(request) { progress ->
                        transition(WidgetAuthoringJobState.Running(request, progress))
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    transition(
                        WidgetAuthoringJobState.Failed(request, WidgetAuthoringJobFailureReason.Unexpected),
                    )
                    return
                }
                when (outcome) {
                    is WidgetAuthoringJobOutcome.Ready -> {
                        transition(WidgetAuthoringJobState.Succeeded(request, outcome.value))
                        return
                    }
                    is WidgetAuthoringJobOutcome.Failure -> {
                        transition(
                            WidgetAuthoringJobState.Failed(request, outcome.reason, outcome.detail),
                        )
                        return
                    }
                }
            }
        } finally {
            @Suppress("DEPRECATION")
            synchronized(this@WidgetAuthoringJobController) {
                if (activeRequest === request) {
                    activeRequest = null
                    job = null
                }
            }
        }
    }

    private fun transition(state: WidgetAuthoringJobState) {
        val current = mutableState.value
        if (current is WidgetAuthoringJobState.Succeeded<*> ||
            current is WidgetAuthoringJobState.Failed ||
            current is WidgetAuthoringJobState.Cancelled
        ) {
            return
        }
        mutableState.value = state
        presenter?.onStateChanged(state)
    }
}
