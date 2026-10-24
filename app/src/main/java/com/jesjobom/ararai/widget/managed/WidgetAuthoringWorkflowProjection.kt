@file:Suppress("CyclomaticComplexMethod")

package com.jesjobom.ararai.widget.managed

import com.google.gson.JsonObject
import com.google.gson.JsonParser

internal enum class WidgetAuthoringStageDisplayStatus {
    Pending,
    Queued,
    Deferred,
    Running,
    AwaitingReview,
    AwaitingRetry,
    Accepted,
    Stale,
    Interrupted,
    Cancelled,
    Completed,
}

internal data class WidgetAuthoringAttemptDetails(
    val attemptId: String,
    val stageRevision: Int,
    val kind: WidgetAuthoringAttemptKind,
    val attemptNumber: Int,
    val status: WidgetAuthoringAttemptStatus,
    val durationMillis: Long?,
    val capturedBytes: Int?,
    val failureCode: WidgetAuthoringStageFailureCode?,
    val capturedArtifact: String?,
)

internal data class WidgetAuthoringStageProjection(
    val stage: WidgetAuthoringStageKey,
    val status: WidgetAuthoringStageDisplayStatus,
    val acceptedCheckpoint: WidgetAuthoringCheckpoint?,
    val attempts: List<WidgetAuthoringAttemptDetails>,
    val warnings: List<WidgetAuthoringWarning> = emptyList(),
)

internal enum class WidgetAuthoringWarning {
    RandomSelectionMayUseFirst,
    FirstSelectionMayUseRandom,
    LastSelectionUnavailable,
    TimeConditionNotEvident,
}

internal data class WidgetAuthoringWorkflowProjection(
    val sessionId: String,
    val sessionRevision: Int,
    val status: WidgetAuthoringSessionStatus,
    val currentStage: WidgetAuthoringStageKey,
    val stages: List<WidgetAuthoringStageProjection>,
    val allowedActions: Set<WidgetAuthoringUserAction>,
)

internal object WidgetAuthoringWorkflowProjector {
    fun project(snapshot: WidgetAuthoringSessionSnapshot): WidgetAuthoringWorkflowProjection {
        val knownStages = buildList {
            add(WidgetAuthoringStageKey.Feasibility)
            add(WidgetAuthoringStageKey.Algorithm)
            addAll(
                (
                    snapshot.checkpoints.map(WidgetAuthoringCheckpoint::stage) +
                        snapshot.attempts.map(WidgetAuthoringAttempt::stage)
                    )
                    .filterIsInstance<WidgetAuthoringStageKey.CallFunction>()
                    .distinctBy(WidgetAuthoringStageKey.CallFunction::stepId),
            )
            (snapshot.session.currentStage as? WidgetAuthoringStageKey.CallFunction)?.let(::add)
            add(WidgetAuthoringStageKey.Plan)
            add(WidgetAuthoringStageKey.Render)
            add(WidgetAuthoringStageKey.Assembly)
        }
        return WidgetAuthoringWorkflowProjection(
            sessionId = snapshot.session.id,
            sessionRevision = snapshot.session.revision,
            status = snapshot.session.status,
            currentStage = snapshot.session.currentStage,
            stages = knownStages.distinct().map { stage ->
                val checkpoint = snapshot.checkpoints.singleOrNull { it.stage == stage }
                val attempts = snapshot.attempts.filter { it.stage == stage }
                WidgetAuthoringStageProjection(
                    stage = stage,
                    status = displayStatus(snapshot, stage, checkpoint, attempts.lastOrNull()),
                    acceptedCheckpoint = checkpoint,
                    attempts = attempts.map { it.toDetails() },
                    warnings = WidgetAuthoringWarningDetector.detect(
                        snapshot.session.instruction,
                        stage,
                        if (
                            stage == snapshot.session.currentStage &&
                            snapshot.session.status == WidgetAuthoringSessionStatus.AwaitingReview
                        ) {
                            attempts.lastOrNull { it.status == WidgetAuthoringAttemptStatus.Succeeded }?.artifact
                        } else {
                            checkpoint?.takeIf { it.status == WidgetAuthoringCheckpointStatus.Accepted }?.artifact
                        },
                    ),
                )
            },
            allowedActions = WidgetAuthoringWorkflowStateMachine.allowedActions(snapshot),
        )
    }

    private fun displayStatus(
        snapshot: WidgetAuthoringSessionSnapshot,
        stage: WidgetAuthoringStageKey,
        checkpoint: WidgetAuthoringCheckpoint?,
        latestAttempt: WidgetAuthoringAttempt?,
    ): WidgetAuthoringStageDisplayStatus = when {
        snapshot.session.status == WidgetAuthoringSessionStatus.ReadyForPreview &&
            stage == WidgetAuthoringStageKey.Assembly -> WidgetAuthoringStageDisplayStatus.Completed
        stage == snapshot.session.currentStage -> when (snapshot.session.status) {
            WidgetAuthoringSessionStatus.AttemptQueued -> WidgetAuthoringStageDisplayStatus.Queued
            WidgetAuthoringSessionStatus.AttemptDeferred -> WidgetAuthoringStageDisplayStatus.Deferred
            WidgetAuthoringSessionStatus.AttemptRunning -> WidgetAuthoringStageDisplayStatus.Running
            WidgetAuthoringSessionStatus.AwaitingReview -> WidgetAuthoringStageDisplayStatus.AwaitingReview
            WidgetAuthoringSessionStatus.AwaitingRetry -> when (latestAttempt?.status) {
                WidgetAuthoringAttemptStatus.Interrupted -> WidgetAuthoringStageDisplayStatus.Interrupted
                WidgetAuthoringAttemptStatus.Cancelled -> WidgetAuthoringStageDisplayStatus.Cancelled
                else -> WidgetAuthoringStageDisplayStatus.AwaitingRetry
            }
            WidgetAuthoringSessionStatus.Completed -> WidgetAuthoringStageDisplayStatus.Completed
            WidgetAuthoringSessionStatus.AwaitingStage,
            WidgetAuthoringSessionStatus.ReadyForPreview,
            -> checkpoint.acceptedStatusOrPending()
        }
        checkpoint?.status == WidgetAuthoringCheckpointStatus.Stale -> WidgetAuthoringStageDisplayStatus.Stale
        else -> checkpoint.acceptedStatusOrPending()
    }

    @Suppress("MaxLineLength")
    private fun WidgetAuthoringCheckpoint?.acceptedStatusOrPending(): WidgetAuthoringStageDisplayStatus = when (this?.status) {
        WidgetAuthoringCheckpointStatus.Accepted -> WidgetAuthoringStageDisplayStatus.Accepted
        WidgetAuthoringCheckpointStatus.Stale -> WidgetAuthoringStageDisplayStatus.Stale
        null -> WidgetAuthoringStageDisplayStatus.Pending
    }

    private fun WidgetAuthoringAttempt.toDetails(): WidgetAuthoringAttemptDetails = WidgetAuthoringAttemptDetails(
        attemptId = id,
        stageRevision = stageRevision,
        kind = kind,
        attemptNumber = attemptNumber,
        status = status,
        durationMillis = if (startedAtMillis != null && completedAtMillis != null) {
            (completedAtMillis - startedAtMillis).coerceAtLeast(0)
        } else {
            null
        },
        capturedBytes = artifact?.toByteArray(Charsets.UTF_8)?.size,
        failureCode = failureCode,
        capturedArtifact = artifact,
    )
}

/** Advisory only: uncertain prompt heuristics must never change stage validity or allowed actions. */
internal object WidgetAuthoringWarningDetector {
    private enum class RequestedSelection { Random, First, Last }

    private val selectionWords = mapOf(
        RequestedSelection.Random to Regex(
            "\\b(?:aleat[oó]ri[oa]s?|rand[oô]mic[oa]s?|random)\\b",
            RegexOption.IGNORE_CASE,
        ),
        RequestedSelection.First to Regex("\\b(?:primeir[oa]s?|first)\\b", RegexOption.IGNORE_CASE),
        RequestedSelection.Last to Regex("\\b(?:[uú]ltim[oa]s?|last)\\b", RegexOption.IGNORE_CASE),
    )
    private val dayWords = Regex("\\bdia\\b|\\bday\\b", RegexOption.IGNORE_CASE)
    private val nightWords = Regex("\\bnoite\\b|\\bnight\\b", RegexOption.IGNORE_CASE)

    fun detect(instruction: String, stage: WidgetAuthoringStageKey, artifact: String?): List<WidgetAuthoringWarning> {
        if (artifact == null) return emptyList()
        return try {
            val root = JsonParser.parseString(artifact).asJsonObject
            when (stage) {
                WidgetAuthoringStageKey.Algorithm -> detectAlgorithm(instruction, root)
                is WidgetAuthoringStageKey.CallFunction -> detectCall(instruction, root)
                else -> emptyList()
            }
        } catch (_: RuntimeException) {
            emptyList()
        }
    }

    private fun detectAlgorithm(instruction: String, root: JsonObject): List<WidgetAuthoringWarning> {
        val steps = root.getAsJsonArray("steps")
        val modes = steps?.mapNotNull { step ->
            val value = step.asJsonObject
            if (value.get("toolId")?.takeUnless { it.isJsonNull }?.asString != "wikipedia_pages") {
                null
            } else {
                value.get("selectionMode")?.takeUnless { it.isJsonNull }?.asString
            }
        }.orEmpty()
        val requested = selectionWords.filterValues { it.containsMatchIn(instruction) }.keys.singleOrNull()
        return when (requested) {
            RequestedSelection.Random -> if ("first" in modes) {
                listOf(WidgetAuthoringWarning.RandomSelectionMayUseFirst)
            } else {
                emptyList()
            }
            RequestedSelection.First -> if ("seeded_random" in modes) {
                listOf(WidgetAuthoringWarning.FirstSelectionMayUseRandom)
            } else {
                emptyList()
            }
            RequestedSelection.Last -> if (modes.isNotEmpty()) {
                listOf(WidgetAuthoringWarning.LastSelectionUnavailable)
            } else {
                emptyList()
            }
            null -> emptyList()
        }
    }

    private fun detectCall(instruction: String, root: JsonObject): List<WidgetAuthoringWarning> {
        val source = root.get("source")?.takeUnless { it.isJsonNull }?.asString
        val mentionsTimeCondition = dayWords.containsMatchIn(instruction) && nightWords.containsMatchIn(instruction)
        return if (mentionsTimeCondition && source != null && !source.contains("currentLocalDateTime")) {
            listOf(WidgetAuthoringWarning.TimeConditionNotEvident)
        } else {
            emptyList()
        }
    }
}
