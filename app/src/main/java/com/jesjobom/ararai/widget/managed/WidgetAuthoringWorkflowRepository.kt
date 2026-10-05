package com.jesjobom.ararai.widget.managed

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

internal interface WidgetAuthoringWorkflowRepository {
    val activeSession: StateFlow<WidgetAuthoringSessionSnapshot?>

    suspend fun initialize(): WidgetAuthoringSessionSnapshot?

    suspend fun create(request: NewWidgetAuthoringSession): WidgetAuthoringSessionSnapshot

    suspend fun startAttempt(command: StartWidgetAuthoringAttempt): StartWidgetAuthoringAttemptResult

    suspend fun markAttemptDeferred(
        sessionId: String,
        attemptId: String,
    ): WidgetAuthoringSessionSnapshot

    suspend fun markAttemptRunning(
        sessionId: String,
        attemptId: String,
    ): WidgetAuthoringSessionSnapshot

    suspend fun completeAttempt(command: CompleteWidgetAuthoringAttempt): WidgetAuthoringSessionSnapshot

    suspend fun acceptCandidate(command: AcceptWidgetAuthoringCandidate): WidgetAuthoringMutationResult

    suspend fun finalize(
        sessionId: String,
        expectedSessionRevision: Int,
        candidate: ConfirmedWidgetRevision,
    ): FinalizeWidgetAuthoringResult

    suspend fun discard(
        sessionId: String,
        expectedSessionRevision: Int,
    ): Boolean
}

internal class DispatcherWidgetAuthoringWorkflowRepository(
    private val store: WidgetAuthoringWorkflowStore,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : WidgetAuthoringWorkflowRepository {
    private val mutableActiveSession = MutableStateFlow<WidgetAuthoringSessionSnapshot?>(null)
    override val activeSession: StateFlow<WidgetAuthoringSessionSnapshot?> = mutableActiveSession.asStateFlow()

    override suspend fun initialize(): WidgetAuthoringSessionSnapshot? = databaseCall {
        store.reconcileInterruptedAuthoring()
    }.also(::publish)

    override suspend fun create(request: NewWidgetAuthoringSession): WidgetAuthoringSessionSnapshot = databaseCall {
        store.createAuthoringSession(request)
    }.also(::publish)

    override suspend fun startAttempt(command: StartWidgetAuthoringAttempt): StartWidgetAuthoringAttemptResult {
        val (result, snapshot) = databaseCall {
            store.startAuthoringAttempt(command) to store.authoringSession(command.sessionId)
        }
        publish(snapshot)
        return result
    }

    override suspend fun markAttemptRunning(
        sessionId: String,
        attemptId: String,
    ): WidgetAuthoringSessionSnapshot = databaseCall {
        store.markAuthoringAttemptRunning(sessionId, attemptId)
    }.also(::publish)

    override suspend fun markAttemptDeferred(
        sessionId: String,
        attemptId: String,
    ): WidgetAuthoringSessionSnapshot = databaseCall {
        store.markAuthoringAttemptDeferred(sessionId, attemptId)
    }.also(::publish)

    override suspend fun completeAttempt(
        command: CompleteWidgetAuthoringAttempt,
    ): WidgetAuthoringSessionSnapshot = databaseCall {
        store.completeAuthoringAttempt(command)
    }.also(::publish)

    override suspend fun acceptCandidate(
        command: AcceptWidgetAuthoringCandidate,
    ): WidgetAuthoringMutationResult {
        val (result, snapshot) = databaseCall {
            store.acceptAuthoringCandidate(command) to store.authoringSession(command.sessionId)
        }
        publish(snapshot)
        return result
    }

    override suspend fun finalize(
        sessionId: String,
        expectedSessionRevision: Int,
        candidate: ConfirmedWidgetRevision,
    ): FinalizeWidgetAuthoringResult {
        val (result, snapshot) = databaseCall {
            val result = store.finalizeAuthoringSession(sessionId, expectedSessionRevision, candidate)
            val snapshot = if (
                result is FinalizeWidgetAuthoringResult.Finalized ||
                result is FinalizeWidgetAuthoringResult.Missing
            ) {
                store.activeAuthoringSession()
            } else {
                store.authoringSession(sessionId)
            }
            result to snapshot
        }
        publish(snapshot)
        return result
    }

    override suspend fun discard(
        sessionId: String,
        expectedSessionRevision: Int,
    ): Boolean {
        val (discarded, snapshot) = databaseCall {
            store.discardAuthoringSession(sessionId, expectedSessionRevision) to store.activeAuthoringSession()
        }
        publish(snapshot)
        return discarded
    }

    private fun publish(snapshot: WidgetAuthoringSessionSnapshot?) {
        mutableActiveSession.value = snapshot
    }

    private suspend fun <T> databaseCall(block: () -> T): T = withContext(dispatcher) { block() }
}
