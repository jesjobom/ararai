package com.jesjobom.ararai.widget.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetRuntimeStandardLibraryTest {
    @Test
    fun `wraps the selected entrypoint with deterministic snapshot helpers`() {
        val wrapped = WidgetRuntimeStandardLibrary.wrap(
            "function plan(runtime) { return runtime.currentLocalDateTime(); }",
            "plan",
        )

        assertTrue(wrapped.contains("function ${WidgetRuntimeStandardLibrary.WRAPPER_ENTRYPOINT}"))
        assertTrue(wrapped.contains("currentLocalDateTime"))
        assertTrue(wrapped.contains("seededIndex"))
        assertTrue(wrapped.contains("return plan.apply"))
        assertFalse(wrapped.contains("Date.now"))
        assertFalse(wrapped.contains("Math.random"))
    }

    @Test
    fun `rejects source that collides with application-owned stdlib identifiers`() {
        assertThrows(IllegalArgumentException::class.java) {
            WidgetRuntimeStandardLibrary.wrap(
                "function plan(runtime) { return __araraiSnapshot; }",
                "plan",
            )
        }
    }

    @Test
    fun `program parser rejects attempts to redefine stdlib identifiers`() {
        val source = "function plan(runtime) { return __araraiCreateRuntime(runtime); } function render(){return {}; }"
        val manifest = WidgetProgramParserTest.validManifest().replace(
            sha256(WidgetProgramParserTest.SOURCE),
            sha256(source),
        )

        assertTrue(
            WidgetProgramParser.parse(manifest, source) is WidgetProgramValidationResult.Invalid,
        )
    }
}
