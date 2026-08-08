@file:Suppress(
    "CyclomaticComplexMethod",
    "MaxLineLength",
    "ReturnCount",
    "SwallowedException",
    "TooGenericExceptionCaught",
)

package com.jesjobom.ararai.math

import com.ezylang.evalex.Expression
import com.ezylang.evalex.config.ExpressionConfiguration
import com.ezylang.evalex.config.MapBasedFunctionDictionary
import com.ezylang.evalex.config.MapBasedOperatorDictionary
import com.ezylang.evalex.functions.basic.AbsFunction
import com.ezylang.evalex.functions.basic.Log10Function
import com.ezylang.evalex.functions.basic.LogFunction
import com.ezylang.evalex.functions.basic.MaxFunction
import com.ezylang.evalex.functions.basic.MinFunction
import com.ezylang.evalex.functions.basic.SqrtFunction
import com.ezylang.evalex.functions.trigonometric.CosFunction
import com.ezylang.evalex.functions.trigonometric.SinFunction
import com.ezylang.evalex.functions.trigonometric.TanFunction
import com.ezylang.evalex.operators.arithmetic.InfixDivisionOperator
import com.ezylang.evalex.operators.arithmetic.InfixMinusOperator
import com.ezylang.evalex.operators.arithmetic.InfixModuloOperator
import com.ezylang.evalex.operators.arithmetic.InfixMultiplicationOperator
import com.ezylang.evalex.operators.arithmetic.InfixPlusOperator
import com.ezylang.evalex.operators.arithmetic.InfixPowerOfOperator
import com.ezylang.evalex.operators.arithmetic.PrefixMinusOperator
import com.ezylang.evalex.operators.arithmetic.PrefixPlusOperator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.math.MathContext
import kotlin.coroutines.coroutineContext

enum class MathResultKind { Exact, Rounded, Approximate }

sealed interface MathEvaluationResult {
    data class Success(val value: String, val kind: MathResultKind) : MathEvaluationResult
    data class Failure(val reason: MathFailureReason) : MathEvaluationResult
}

enum class MathFailureReason {
    InvalidExpression,
    UnsupportedOperation,
    DomainError,
    NonFiniteResult,
    ComplexityLimit,
    TimedOut,
    Cancelled,
    Unavailable,
}

fun interface LocalMathEngine {
    suspend fun evaluate(expression: String): MathEvaluationResult
}

class EvalExLocalMathEngine(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : LocalMathEngine {
    override suspend fun evaluate(expression: String): MathEvaluationResult = try {
        withTimeout(TIMEOUT_MILLIS) {
            withContext(dispatcher) {
                coroutineContext.ensureActive()
                validate(expression)?.let { return@withContext MathEvaluationResult.Failure(it) }
                val value = Expression(expression, configuration).evaluate().numberValue
                coroutineContext.ensureActive()
                val canonical = value.stripTrailingZeros().toPlainString()
                if (canonical.equals("nan", true) || canonical.contains("infinity", true)) {
                    MathEvaluationResult.Failure(MathFailureReason.NonFiniteResult)
                } else {
                    MathEvaluationResult.Success(canonical, classify(expression))
                }
            }
        }
    } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
        MathEvaluationResult.Failure(MathFailureReason.TimedOut)
    } catch (_: CancellationException) {
        MathEvaluationResult.Failure(MathFailureReason.Cancelled)
    } catch (error: ArithmeticException) {
        MathEvaluationResult.Failure(MathFailureReason.DomainError)
    } catch (error: Exception) {
        MathEvaluationResult.Failure(error.toMathFailureReason(expression))
    }

    private fun validate(raw: String): MathFailureReason? {
        val value = raw.trim()
        if (value.isEmpty()) return MathFailureReason.InvalidExpression
        if (value.length > MAX_INPUT_LENGTH) return MathFailureReason.ComplexityLimit
        if (!ALLOWED_CHARS.matches(value)) return MathFailureReason.UnsupportedOperation
        if (IMPLICIT_MULTIPLICATION.containsMatchIn(value)) return MathFailureReason.UnsupportedOperation
        if (value.count { it == '(' } != value.count { it == ')' }) return MathFailureReason.InvalidExpression
        var depth = 0
        var tokens = 0
        TOKEN.findAll(value).forEach { match ->
            tokens++
            if (tokens > MAX_TOKENS) return MathFailureReason.ComplexityLimit
            val token = match.value.lowercase()
            if (token.first().isLetter() && token !in ALLOWED_IDENTIFIERS) return MathFailureReason.UnsupportedOperation
            if (token.first().isDigit() && token.length > MAX_LITERAL_LENGTH) return MathFailureReason.ComplexityLimit
        }
        value.forEach {
            if (it == '(' && ++depth > MAX_DEPTH) return MathFailureReason.ComplexityLimit
            if (it == ')') depth--
        }
        EXPONENT.findAll(value).forEach {
            val exponent = it.groupValues[1].toIntOrNull() ?: return MathFailureReason.ComplexityLimit
            if (kotlin.math.abs(exponent) > MAX_EXPONENT) return MathFailureReason.ComplexityLimit
        }
        return null
    }

    private fun classify(expression: String): MathResultKind = when {
        APPROXIMATE.containsMatchIn(expression) -> MathResultKind.Approximate
        '/' in expression || SQRT.containsMatchIn(expression) -> MathResultKind.Rounded
        else -> MathResultKind.Exact
    }

    private fun Exception.toMathFailureReason(expression: String): MathFailureReason {
        val details = generateSequence(this as Throwable?) { it.cause }
            .mapNotNull(Throwable::message)
            .joinToString(" ")
            .lowercase()
        return if (DOMAIN_ERROR_TERMS.any(details::contains) || OBVIOUS_DOMAIN_ERROR.containsMatchIn(expression)) {
            MathFailureReason.DomainError
        } else {
            MathFailureReason.InvalidExpression
        }
    }

    companion object {
        const val MAX_INPUT_LENGTH = 512
        const val MAX_TOKENS = 128
        const val MAX_DEPTH = 16
        const val MAX_LITERAL_LENGTH = 80
        const val MAX_EXPONENT = 1_000
        const val TIMEOUT_MILLIS = 2_000L
        private val ALLOWED_IDENTIFIERS = setOf("abs", "sqrt", "min", "max", "ln", "log10", "sin", "cos", "tan", "pi", "e")
        private val ALLOWED_CHARS = Regex("[0-9A-Za-z_+\\-*/%^().,\\s]+")
        private val TOKEN = Regex("[A-Za-z_][A-Za-z_0-9]*|(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?|[+\\-*/%^(),]")
        private val EXPONENT = Regex("\\^\\s*[+-]?(\\d+)")
        private val IMPLICIT_MULTIPLICATION = Regex("(?:\\d|\\))\\s*\\(|\\)\\s*(?:\\d|[A-Za-z_])")
        private val APPROXIMATE = Regex("(?i)\\b(?:ln|log10|sin|cos|tan)\\s*\\(|\\^(?!\\s*[+-]?\\d+\\b)")
        private val SQRT = Regex("(?i)\\bsqrt\\s*\\(")
        private val DOMAIN_ERROR_TERMS = setOf("division by zero", "divide by zero", "domain", "non-negative", "logarithm")
        private val OBVIOUS_DOMAIN_ERROR = Regex("(?i)(?:sqrt\\s*\\(\\s*-|(?:ln|log10)\\s*\\(\\s*(?:0|-))")
        private val configuration = ExpressionConfiguration.builder()
            .mathContext(MathContext.DECIMAL128)
            .operatorDictionary(
                MapBasedOperatorDictionary().apply {
                    listOf(
                        "+" to InfixPlusOperator(),
                        "-" to InfixMinusOperator(),
                        "*" to InfixMultiplicationOperator(),
                        "/" to InfixDivisionOperator(),
                        "%" to InfixModuloOperator(),
                        "^" to InfixPowerOfOperator(),
                        "+" to PrefixPlusOperator(),
                        "-" to PrefixMinusOperator(),
                    ).forEach { (token, operator) -> addOperator(token, operator) }
                },
            )
            .functionDictionary(
                MapBasedFunctionDictionary().apply {
                    addFunction("abs", AbsFunction())
                    addFunction("sqrt", SqrtFunction())
                    addFunction("min", MinFunction())
                    addFunction("max", MaxFunction())
                    addFunction("ln", LogFunction())
                    addFunction("log10", Log10Function())
                    addFunction("sin", SinFunction())
                    addFunction("cos", CosFunction())
                    addFunction("tan", TanFunction())
                },
            )
            .defaultConstants(ExpressionConfiguration.StandardConstants.filterKeys { it.lowercase() in setOf("pi", "e") })
            .arraysAllowed(false).structuresAllowed(false).binaryAllowed(false)
            .implicitMultiplicationAllowed(false).singleQuoteStringLiteralsAllowed(false)
            .lenientMode(false).maxRecursionDepth(MAX_DEPTH)
            .build()
    }
}
