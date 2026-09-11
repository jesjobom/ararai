package com.jesjobom.ararai.widget.managed

import com.google.gson.JsonParser
import java.math.BigDecimal

internal data class WidgetPresentationCache(
    val widgetId: String,
    val revision: Int,
    val presentationJson: String,
    val completedAtMillis: Long,
) {
    init {
        requireValidWidgetId(widgetId)
        require(revision > 0)
        require(completedAtMillis >= 0)
        require(presentationJson.utf8Size() <= ManagedWidgetPolicy.MAX_CACHED_PRESENTATION_BYTES)
        require(runCatching { JsonParser.parseString(presentationJson).isJsonObject }.getOrDefault(false))
    }
}

internal enum class WidgetRunOutcome { InProgress, Success, ControlledFailure, Cancelled, Discarded }

/** Stable, allowlisted diagnostics. Arbitrary exception/provider text must never enter run history. */
internal enum class WidgetRunDiagnostic {
    InvalidProgram,
    IncompatibleProgram,
    IntegrityMismatch,
    CapabilityDenied,
    PolicyViolation,
    InvalidPlan,
    InvalidPresentation,
    ScriptError,
    ResourceLimit,
    Cancelled,
    RuntimeUnavailable,
    ToolUnavailable,
    LeaseExpired,
}

internal data class NewWidgetRun(
    val widgetId: String,
    val revision: Int,
    val startedAtMillis: Long,
    val completedAtMillis: Long?,
    val plannedToolIds: List<String>,
    val outcome: WidgetRunOutcome,
    val diagnostic: WidgetRunDiagnostic?,
) {
    init {
        requireValidWidgetId(widgetId)
        require(revision > 0)
        require(startedAtMillis >= 0)
        require(completedAtMillis == null || completedAtMillis >= startedAtMillis)
        require(plannedToolIds.size <= ManagedWidgetPolicy.MAX_RUN_TOOL_IDS)
        require(plannedToolIds.distinct().size == plannedToolIds.size)
        require(plannedToolIds.all(TOOL_ID_PATTERN::matches))
        when (outcome) {
            WidgetRunOutcome.InProgress -> require(completedAtMillis == null && diagnostic == null)
            WidgetRunOutcome.Success,
            WidgetRunOutcome.Discarded,
            -> require(completedAtMillis != null && diagnostic == null)
            WidgetRunOutcome.ControlledFailure -> require(
                completedAtMillis != null && diagnostic != null && diagnostic != WidgetRunDiagnostic.Cancelled,
            )
            WidgetRunOutcome.Cancelled -> require(
                completedAtMillis != null && diagnostic == WidgetRunDiagnostic.Cancelled,
            )
        }
    }
}

internal data class WidgetRunRecord(
    val id: String,
    val widgetId: String,
    val revision: Int,
    val startedAtMillis: Long,
    val completedAtMillis: Long?,
    val durationMillis: Long?,
    val plannedToolIds: List<String>,
    val outcome: WidgetRunOutcome,
    val diagnostic: WidgetRunDiagnostic?,
)

internal data class ManagedWidgetRunLease(
    val runId: String,
    val widgetId: String,
    val revision: Int,
    val consentDigest: String,
    val startedAtMillis: Long,
    val program: WidgetProgramRevision,
)

internal sealed interface ManagedWidgetLeaseResult {
    data class Acquired(val lease: ManagedWidgetRunLease) : ManagedWidgetLeaseResult
    data object Missing : ManagedWidgetLeaseResult
    data object Disabled : ManagedWidgetLeaseResult
    data object Busy : ManagedWidgetLeaseResult
}

internal data class ManagedWidgetRunCompletion(
    val completedAtMillis: Long,
    val plannedToolIds: List<String>,
    val outcome: WidgetRunOutcome,
    val diagnostic: WidgetRunDiagnostic?,
    val presentationJson: String? = null,
    val observations: List<NewWidgetObservation> = emptyList(),
    val disableExecution: Boolean = false,
) {
    init {
        require(completedAtMillis >= 0)
        require(plannedToolIds.size <= ManagedWidgetPolicy.MAX_RUN_TOOL_IDS)
        require(plannedToolIds.distinct().size == plannedToolIds.size)
        require(plannedToolIds.all(TOOL_ID_PATTERN::matches))
        require(outcome != WidgetRunOutcome.InProgress)
        require((outcome == WidgetRunOutcome.Success) == (presentationJson != null))
        require(!disableExecution || outcome == WidgetRunOutcome.ControlledFailure)
        NewWidgetRun(
            widgetId = "validation",
            revision = 1,
            startedAtMillis = 0,
            completedAtMillis = completedAtMillis,
            plannedToolIds = plannedToolIds,
            outcome = outcome,
            diagnostic = diagnostic,
        )
        if (presentationJson != null) {
            require(presentationJson.utf8Size() <= ManagedWidgetPolicy.MAX_CACHED_PRESENTATION_BYTES)
            require(runCatching { JsonParser.parseString(presentationJson).isJsonObject }.getOrDefault(false))
        }
        require(outcome == WidgetRunOutcome.Success || observations.isEmpty())
    }
}

internal enum class ManagedWidgetCommitResult { Stored, Discarded }

internal sealed interface WidgetObservationValue {
    data class Text(val value: String) : WidgetObservationValue {
        init {
            require(value.length <= ManagedWidgetPolicy.MAX_OBSERVATION_STRING_CHARS)
        }
    }

    data class Number(val value: String) : WidgetObservationValue {
        init {
            require(value.length <= ManagedWidgetPolicy.MAX_OBSERVATION_STRING_CHARS)
            require(runCatching { BigDecimal(value).toPlainString() == value }.getOrDefault(false))
        }
    }

    data class BooleanValue(val value: Boolean) : WidgetObservationValue
}

internal data class NewWidgetObservation(
    val name: String,
    val observedAtMillis: Long,
    val value: WidgetObservationValue,
) {
    init {
        require(OBSERVATION_NAME_PATTERN.matches(name))
        require(observedAtMillis >= 0)
    }
}

internal data class WidgetObservation(
    val widgetId: String,
    val name: String,
    val observedAtMillis: Long,
    val value: WidgetObservationValue,
)

internal data class WidgetHistoryEntry(
    val name: String,
    val observedAtMillis: Long,
    val value: WidgetObservationValue,
)

internal interface WidgetOwnedHistory {
    fun list(): List<WidgetHistoryEntry>

    fun nearest(
        name: String,
        targetTimestampMillis: Long,
        toleranceMillis: Long,
    ): WidgetHistoryEntry?
}

internal fun ManagedWidgetStore.ownedHistory(widgetId: String): WidgetOwnedHistory {
    requireValidWidgetId(widgetId)
    return object : WidgetOwnedHistory {
        override fun list(): List<WidgetHistoryEntry> = listObservations(widgetId).map(WidgetObservation::historyEntry)

        override fun nearest(
            name: String,
            targetTimestampMillis: Long,
            toleranceMillis: Long,
        ): WidgetHistoryEntry? = nearestObservation(
            widgetId,
            name,
            targetTimestampMillis,
            toleranceMillis,
        )?.historyEntry()
    }
}

private fun WidgetObservation.historyEntry() = WidgetHistoryEntry(name, observedAtMillis, value)

internal fun requireValidWidgetId(widgetId: String) {
    require(WIDGET_ID_PATTERN.matches(widgetId)) { "Invalid managed widget id" }
}

internal val WIDGET_ID_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
internal val TOOL_ID_PATTERN = Regex("[a-z][a-z0-9_]{0,63}")
internal val OBSERVATION_NAME_PATTERN = Regex("[a-z][a-z0-9_]{0,63}")
