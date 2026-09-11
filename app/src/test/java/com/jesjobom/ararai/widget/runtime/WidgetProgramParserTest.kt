package com.jesjobom.ararai.widget.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetProgramParserTest {
    @Test
    fun `accepts intact supported program without evaluating source`() {
        val result = WidgetProgramParser.parse(validManifest(), SOURCE)

        assertTrue(result is WidgetProgramValidationResult.Valid)
        val program = (result as WidgetProgramValidationResult.Valid).program
        assertEquals(SOURCE, program.source)
        assertEquals(setOf(WidgetToolCapability("weather_lookup", 1)), program.manifest.capabilities.tools)
    }

    @Test
    fun `canonical manifest is stable across field order and number spelling`() {
        val first = validManifest()
        val second = validManifest().replace(
            "\"memoryBytes\":8388608,\"stackBytes\":524288",
            "\"stackBytes\":524288.0,\"memoryBytes\":8.388608e6",
        )

        val firstProgram = (WidgetProgramParser.parse(first, SOURCE) as WidgetProgramValidationResult.Valid).program
        val secondProgram = (WidgetProgramParser.parse(second, SOURCE) as WidgetProgramValidationResult.Valid).program

        assertEquals(firstProgram.canonicalManifestJson, secondProgram.canonicalManifestJson)
    }

    @Test
    fun `rejects unknown missing and additional root fields`() {
        assertCode(
            WidgetRuntimeFailureCode.InvalidProgram,
            validManifest().replace("\"schemaVersion\":1,", ""),
        )
        assertCode(
            WidgetRuntimeFailureCode.InvalidProgram,
            validManifest().replaceFirst("{", "{\"unexpected\":true,"),
        )
        assertCode(
            WidgetRuntimeFailureCode.InvalidProgram,
            validManifest().replace("\"language\":\"javascript\"", "\"lang\":\"javascript\""),
        )
    }

    @Test
    fun `rejects duplicate fields and trailing JSON`() {
        assertCode(
            WidgetRuntimeFailureCode.InvalidProgram,
            validManifest().replaceFirst("{", "{\"schemaVersion\":1,"),
        )
        assertCode(WidgetRuntimeFailureCode.InvalidProgram, validManifest() + "{}")
    }

    @Test
    fun `rejects unsupported schema language and api versions`() {
        assertCode(
            WidgetRuntimeFailureCode.IncompatibleProgram,
            validManifest().replace("\"schemaVersion\":1", "\"schemaVersion\":2"),
        )
        assertCode(
            WidgetRuntimeFailureCode.IncompatibleProgram,
            validManifest().replace("\"language\":\"javascript\"", "\"language\":\"mvel\""),
        )
        assertCode(
            WidgetRuntimeFailureCode.IncompatibleProgram,
            validManifest().replace("\"apiVersion\":1", "\"apiVersion\":9"),
        )
    }

    @Test
    fun `rejects malformed identifiers duplicate capabilities and unsupported capabilities`() {
        assertCode(
            WidgetRuntimeFailureCode.InvalidProgram,
            validManifest().replace("\"weather_lookup\"", "\"../weather\""),
        )
        assertCode(
            WidgetRuntimeFailureCode.InvalidProgram,
            validManifest().replace("\"runtime\":[\"locale\"", "\"runtime\":[\"locale\",\"locale\""),
        )
        assertCode(
            WidgetRuntimeFailureCode.InvalidProgram,
            validManifest().replace("\"locale\"", "\"credentials\""),
        )
    }

    @Test
    fun `rejects limits above fixed policy`() {
        assertCode(
            WidgetRuntimeFailureCode.PolicyViolation,
            validManifest().replace("\"maxToolCalls\":4", "\"maxToolCalls\":5"),
        )
        assertCode(
            WidgetRuntimeFailureCode.PolicyViolation,
            validManifest().replace("\"memoryBytes\":8388608", "\"memoryBytes\":8388609"),
        )
        assertCode(
            WidgetRuntimeFailureCode.PolicyViolation,
            validManifest().replace("\"executionMillis\":250", "\"executionMillis\":251"),
        )
    }

    @Test
    fun `rejects malformed and mismatched source digest`() {
        assertCode(
            WidgetRuntimeFailureCode.InvalidProgram,
            validManifest().replace(sha256(SOURCE), "abc"),
        )
        assertCode(WidgetRuntimeFailureCode.IntegrityMismatch, validManifest(), "$SOURCE\n// altered")
    }

    @Test
    fun `rejects oversized manifest source and nested content`() {
        assertCode(
            WidgetRuntimeFailureCode.InvalidProgram,
            " ".repeat(WidgetRuntimePolicy.MAX_MANIFEST_BYTES + 1),
        )
        assertCode(
            WidgetRuntimeFailureCode.InvalidProgram,
            validManifest(),
            "x".repeat(WidgetRuntimePolicy.MAX_SOURCE_BYTES + 1),
        )
        val deep = "[".repeat(20) + "]".repeat(20)
        assertCode(
            WidgetRuntimeFailureCode.InvalidProgram,
            validManifest().replace("[\"locale\",\"timezone\",\"local_time\",\"seed\"]", deep),
        )
    }

    private fun assertCode(
        expected: WidgetRuntimeFailureCode,
        manifest: String,
        source: String = SOURCE,
    ) {
        assertEquals(
            WidgetProgramValidationResult.Invalid(expected),
            WidgetProgramParser.parse(manifest, source),
        )
    }

    companion object {
        const val SOURCE =
            "function plan(context) { return []; } " +
                "function render(context, outcomes, state) { return state; }"

        fun validManifest(): String = """
            {
              "schemaVersion":1,
              "language":"javascript",
              "apiVersion":1,
              "entrypoints":{"plan":"plan","render":"render"},
              "capabilities":{
                "tools":[{"id":"weather_lookup","version":1}],
                "runtime":["locale","timezone","local_time","seed"],
                "presentation":["card","column","row","text","value","icon","https_link"]
              },
              "limits":{"memoryBytes":8388608,"stackBytes":524288,"executionMillis":250,"maxToolCalls":4,"maxOutputBytes":65536},
              "sourceSha256":"${sha256(SOURCE)}"
            }
        """.trimIndent()
    }
}
