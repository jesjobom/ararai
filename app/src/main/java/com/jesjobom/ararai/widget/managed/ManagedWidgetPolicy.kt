package com.jesjobom.ararai.widget.managed

import com.jesjobom.ararai.widget.runtime.WidgetRuntimePolicy

/** Checked-in application maxima for durable managed-widget state and authoring. */
internal object ManagedWidgetPolicy {
    const val MAX_WIDGETS = 50
    const val MAX_REVISIONS_PER_WIDGET = 20

    val SUPPORTED_PERIODIC_INTERVAL_HOURS = setOf(1L, 6L, 12L, 24L)

    const val MAX_CACHED_PRESENTATION_BYTES = WidgetRuntimePolicy.MAX_OUTPUT_BYTES

    const val MAX_OBSERVATIONS_PER_WIDGET = 256
    const val MAX_OBSERVATION_AGE_DAYS = 90
    const val MAX_OBSERVATION_NAME_CHARS = 64
    const val MAX_OBSERVATION_STRING_CHARS = WidgetRuntimePolicy.MAX_STRING_CHARS

    const val MAX_RUNS_PER_WIDGET = 100
    const val MAX_RUN_AGE_DAYS = 30
    const val MAX_RUN_TOOL_IDS = WidgetRuntimePolicy.MAX_TOOL_CALLS
    const val MAX_EXECUTION_LEASE_MILLIS = 5 * 60 * 1000L

    const val MAX_PROGRAM_SOURCE_BYTES = WidgetRuntimePolicy.MAX_SOURCE_BYTES
    const val MAX_PROPOSAL_BYTES = 48 * 1024
    const val MAX_PROPOSAL_CONTEXT_BYTES = 64 * 1024

    const val MAX_PRESENTATION_DEPTH = WidgetRuntimePolicy.MAX_PRESENTATION_DEPTH
    const val MAX_PRESENTATION_NODES = WidgetRuntimePolicy.MAX_PRESENTATION_NODES
    const val MAX_PRESENTATION_CHILDREN = WidgetRuntimePolicy.MAX_PRESENTATION_CHILDREN
    const val MAX_PRESENTATION_TEXT_CHARS = WidgetRuntimePolicy.MAX_TEXT_CHARS
    const val MAX_PRESENTATION_URL_CHARS = 2_048
}
