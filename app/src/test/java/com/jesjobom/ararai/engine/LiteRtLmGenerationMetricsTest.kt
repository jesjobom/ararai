package com.jesjobom.ararai.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class LiteRtLmGenerationMetricsTest {
    @Test
    fun `preserves cumulative native context count separately from last phase counts`() {
        val metrics = liteRtLmGenerationMetrics(
            timeToFirstTokenInSecond = 0.1,
            prefillTokenCount = 496,
            prefillTokensPerSecond = 100.0,
            decodeTokenCount = 20,
            decodeTokensPerSecond = 30.0,
            contextTokenCount = 2_048,
        )

        assertEquals(496, metrics.prefillTokenCount)
        assertEquals(20, metrics.decodeTokenCount)
        assertEquals(2_048, metrics.contextTokenCount)
    }
}
