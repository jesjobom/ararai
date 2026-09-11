package com.jesjobom.ararai.widget.managed

import com.jesjobom.ararai.widget.runtime.WidgetRuntimePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagedWidgetPolicyTest {
    @Test
    fun `managed storage and schedule limits are finite and conservative`() {
        assertEquals(50, ManagedWidgetPolicy.MAX_WIDGETS)
        assertEquals(20, ManagedWidgetPolicy.MAX_REVISIONS_PER_WIDGET)
        assertEquals(setOf(1L, 6L, 12L, 24L), ManagedWidgetPolicy.SUPPORTED_PERIODIC_INTERVAL_HOURS)
        assertTrue(ManagedWidgetPolicy.SUPPORTED_PERIODIC_INTERVAL_HOURS.all { it >= 1L })
        assertEquals(256, ManagedWidgetPolicy.MAX_OBSERVATIONS_PER_WIDGET)
        assertEquals(90, ManagedWidgetPolicy.MAX_OBSERVATION_AGE_DAYS)
        assertEquals(100, ManagedWidgetPolicy.MAX_RUNS_PER_WIDGET)
        assertEquals(30, ManagedWidgetPolicy.MAX_RUN_AGE_DAYS)
    }

    @Test
    fun `managed payload limits never expand the sandbox boundary`() {
        assertEquals(WidgetRuntimePolicy.MAX_SOURCE_BYTES, ManagedWidgetPolicy.MAX_PROGRAM_SOURCE_BYTES)
        assertEquals(WidgetRuntimePolicy.MAX_OUTPUT_BYTES, ManagedWidgetPolicy.MAX_CACHED_PRESENTATION_BYTES)
        assertEquals(WidgetRuntimePolicy.MAX_TOOL_CALLS, ManagedWidgetPolicy.MAX_RUN_TOOL_IDS)
        assertEquals(WidgetRuntimePolicy.MAX_STRING_CHARS, ManagedWidgetPolicy.MAX_OBSERVATION_STRING_CHARS)
        assertEquals(WidgetRuntimePolicy.MAX_PRESENTATION_DEPTH, ManagedWidgetPolicy.MAX_PRESENTATION_DEPTH)
        assertEquals(WidgetRuntimePolicy.MAX_PRESENTATION_NODES, ManagedWidgetPolicy.MAX_PRESENTATION_NODES)
        assertEquals(WidgetRuntimePolicy.MAX_PRESENTATION_CHILDREN, ManagedWidgetPolicy.MAX_PRESENTATION_CHILDREN)
        assertEquals(WidgetRuntimePolicy.MAX_TEXT_CHARS, ManagedWidgetPolicy.MAX_PRESENTATION_TEXT_CHARS)
        assertTrue(ManagedWidgetPolicy.MAX_PROPOSAL_CONTEXT_BYTES <= WidgetRuntimePolicy.MAX_JSON_BYTES)
    }
}
