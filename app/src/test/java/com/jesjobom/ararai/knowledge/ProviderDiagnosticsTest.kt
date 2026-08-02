package com.jesjobom.ararai.knowledge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderDiagnosticsTest {
    @Test
    fun `redacts supplied secrets and authorization forms`() {
        val result =
            redactedProviderError(
                IllegalStateException(
                    "token=private-value Authorization: Bearer second-secret x-api-key=third-secret",
                ),
                listOf("private-value"),
            )

        assertFalse(result.contains("private-value"))
        assertFalse(result.contains("second-secret"))
        assertFalse(result.contains("third-secret"))
        assertTrue(result.contains("[REDACTED]"))
    }
}
