package com.jesjobom.ararai.widget.managed

import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.LocalModel
import com.jesjobom.ararai.widget.runtime.WidgetRuntimeContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal data class WidgetAuthoringAttemptExecution(
    val model: LocalModel,
    val inference: InferenceConfig,
    val prompt: WidgetAuthoringPrompt,
    val runtimeContext: WidgetRuntimeContext,
)

internal sealed interface SubmitWidgetAuthoringAttemptResult {
    data class Started(val attemptId: String) : SubmitWidgetAuthoringAttemptResult
    data class Duplicate(val attemptId: String) : SubmitWidgetAuthoringAttemptResult
    data class Stale(val currentSessionRevision: Int) : SubmitWidgetAuthoringAttemptResult
    data object Busy : SubmitWidgetAuthoringAttemptResult
}

internal class ResumableWidgetAuthoringController(
    private val scope: CoroutineScope,
    private val repository: WidgetAuthoringWorkflowRepository,
    private val executor: WidgetAuthoringAttemptExecutor,
    private val onSuccessfulAttempt: suspend (
        WidgetAuthoringSessionSnapshot,
        WidgetAuthoringAttemptExecution,
    ) -> Unit = { _, _ -> },
) {
    private val mutex = Mutex()

    @Volatile
    private var activeJob: Job? = null
    private val mutableAutoProgressionActive = MutableStateFlow(false)
    val autoProgressionActive: StateFlow<Boolean> = mutableAutoProgressionActive.asStateFlow()

    val activeSession: StateFlow<WidgetAuthoringSessionSnapshot?> = repository.activeSession

    suspend fun initialize(): WidgetAuthoringSessionSnapshot? = mutex.withLock {
        repository.initialize()
    }

    suspend fun createSession(request: NewWidgetAuthoringSession): WidgetAuthoringSessionSnapshot = mutex.withLock {
        repository.activeSession.value ?: repository.create(request)
    }

    suspend fun submitAttempt(
        command: StartWidgetAuthoringAttempt,
        execution: WidgetAuthoringAttemptExecution,
    ): SubmitWidgetAuthoringAttemptResult = mutex.withLock {
        repository.activeSession.value?.attempts
            ?.firstOrNull { it.sessionId == command.sessionId && it.actionId == command.actionId }
            ?.let { return@withLock SubmitWidgetAuthoringAttemptResult.Duplicate(it.id) }
        if (activeJob?.isActive == true) return@withLock SubmitWidgetAuthoringAttemptResult.Busy
        when (val started = repository.startAttempt(command)) {
            is StartWidgetAuthoringAttemptResult.Stale -> {
                SubmitWidgetAuthoringAttemptResult.Stale(started.currentSessionRevision)
            }
            is StartWidgetAuthoringAttemptResult.Duplicate -> {
                SubmitWidgetAuthoringAttemptResult.Duplicate(started.attempt.id)
            }
            is StartWidgetAuthoringAttemptResult.Started -> {
                mutableAutoProgressionActive.value = true
                activeJob = scope.launch {
                    try {
                        val completed = try {
                            executor.run(
                                execution.model,
                                execution.inference,
                                execution.prompt,
                                execution.runtimeContext,
                            )
                        } finally {
                            val runningJob = currentCoroutineContext()[Job]
                            withContext(NonCancellable) {
                                mutex.withLock {
                                    if (activeJob === runningJob) activeJob = null
                                }
                            }
                        }
                        if (completed.session.status == WidgetAuthoringSessionStatus.AwaitingReview &&
                            completed.attempts.lastOrNull()?.status == WidgetAuthoringAttemptStatus.Succeeded
                        ) {
                            onSuccessfulAttempt(completed, execution)
                        }
                    } finally {
                        if (activeSession.value?.session?.status !in ACTIVE_ATTEMPT_STATUSES) {
                            mutableAutoProgressionActive.value = false
                        }
                    }
                }
                SubmitWidgetAuthoringAttemptResult.Started(started.attempt.id)
            }
        }
    }

    suspend fun acceptCandidate(
        command: AcceptWidgetAuthoringCandidate,
    ): WidgetAuthoringMutationResult = mutex.withLock {
        require(activeJob?.isActive != true) { "Cannot accept a candidate while an attempt is active" }
        repository.acceptCandidate(command)
    }

    suspend fun finalize(
        sessionId: String,
        expectedSessionRevision: Int,
        candidate: ConfirmedWidgetRevision,
    ): FinalizeWidgetAuthoringResult = mutex.withLock {
        require(activeJob?.isActive != true) { "Cannot finalize while an attempt is active" }
        repository.finalize(sessionId, expectedSessionRevision, candidate)
    }

    suspend fun discard(
        sessionId: String,
        expectedSessionRevision: Int,
    ): Boolean = mutex.withLock {
        require(activeJob?.isActive != true) { "Cannot discard while an attempt is active" }
        repository.discard(sessionId, expectedSessionRevision)
    }

    fun cancelActiveAttempt(): Boolean = activeJob
        ?.takeIf(Job::isActive)
        ?.also { it.cancel() }
        ?.let { true }
        ?: false

    suspend fun cancelExpectedAttempt(sessionId: String, attemptId: String): Boolean = mutex.withLock {
        val current = activeSession.value?.session
        if (current?.id != sessionId || current.activeAttemptId != attemptId) return@withLock false
        cancelActiveAttempt()
    }

    private companion object {
        val ACTIVE_ATTEMPT_STATUSES = setOf(
            WidgetAuthoringSessionStatus.AttemptQueued,
            WidgetAuthoringSessionStatus.AttemptDeferred,
            WidgetAuthoringSessionStatus.AttemptRunning,
        )
    }
}
