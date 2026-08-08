@file:Suppress("MaxLineLength")

package com.jesjobom.ararai.math

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class EvalExLocalMathEngineTest {
    private val engine = EvalExLocalMathEngine()

    @Test fun arithmeticAndPrecedence() = runBlocking {
        assertEquals(MathEvaluationResult.Success("14", MathResultKind.Exact), engine.evaluate("2 + 3 * 4"))
        assertEquals(MathEvaluationResult.Success("20", MathResultKind.Exact), engine.evaluate("(2 + 3) * 4"))
    }

    @Test fun supportedFunctionsAndConstants() = runBlocking {
        assertSuccess(engine.evaluate("sqrt(81)"), "9")
        assertSuccess(engine.evaluate("max(2, abs(-5))"), "5")
        assertTrue(engine.evaluate("sin(pi / 2)") is MathEvaluationResult.Success)
    }

    @Test fun decimalScientificAndPrecisionClassification() = runBlocking {
        assertEquals(MathEvaluationResult.Success("1000.25", MathResultKind.Exact), engine.evaluate("1e3 + .25"))
        val division = engine.evaluate("1 / 3") as MathEvaluationResult.Success
        assertEquals(MathResultKind.Rounded, division.kind)
        assertTrue(division.value.startsWith("0.3333333333333333333333333333333333"))
        assertEquals(MathResultKind.Approximate, (engine.evaluate("cos(0)") as MathEvaluationResult.Success).kind)
    }

    @Test fun domainErrorsNeverFabricateValues() = runBlocking {
        listOf("1/0", "sqrt(-1)", "ln(0)").forEach { expression ->
            assertEquals(
                "Expected domain failure for $expression",
                MathFailureReason.DomainError,
                (engine.evaluate(expression) as MathEvaluationResult.Failure).reason,
            )
        }
    }

    @Test fun rejectsUnsupportedAndAdversarialInput() = runBlocking {
        listOf("random()", "x=2", "2(3)", "'text'", "[1,2]", "java.lang.Runtime").forEach {
            assertTrue("Expected failure for $it", engine.evaluate(it) is MathEvaluationResult.Failure)
        }
    }

    @Test fun enforcesComplexityLimits() = runBlocking {
        assertEquals(MathFailureReason.ComplexityLimit, (engine.evaluate("1".repeat(81)) as MathEvaluationResult.Failure).reason)
        assertEquals(MathFailureReason.ComplexityLimit, (engine.evaluate("2^1001") as MathEvaluationResult.Failure).reason)
        assertEquals(MathFailureReason.ComplexityLimit, (engine.evaluate("(".repeat(17) + "1" + ")".repeat(17)) as MathEvaluationResult.Failure).reason)
    }

    @Test fun localeIndependentAndConcurrent() = runBlocking {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val results = List(20) { async { engine.evaluate("1.5 + 2.25") } }.awaitAll()
            results.forEach { assertSuccess(it, "3.75") }
        } finally {
            Locale.setDefault(previous)
        }
    }

    private fun assertSuccess(result: MathEvaluationResult, expected: String) {
        assertTrue(result is MathEvaluationResult.Success)
        assertEquals(expected, (result as MathEvaluationResult.Success).value)
    }
}
