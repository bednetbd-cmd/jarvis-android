package com.jarvis.assistant.core.tools

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cbrt
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.cosh
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.math.tanh

enum class AngleUnit {
    DEG,
    RAD
}

object ScientificCalculator {

    fun evaluate(
        expression: String,
        defaultAngleUnit: AngleUnit = AngleUnit.DEG
    ): Result<Double> {
        val raw = expression.trim()
        if (raw.isEmpty()) {
            return Result.failure(IllegalArgumentException("Enter a mathematical expression."))
        }

        val normalized = raw
            .replace("×", "*")
            .replace("÷", "/")
            .replace("−", "-")
            .replace("π", "pi")
            .replace("√", "sqrt")

        val effectiveUnit = when {
            Regex("""\brad(?:ians?)?\b""", RegexOption.IGNORE_CASE).containsMatchIn(normalized) -> AngleUnit.RAD
            Regex("""\bdeg(?:rees?)?\b""", RegexOption.IGNORE_CASE).containsMatchIn(normalized) -> AngleUnit.DEG
            else -> defaultAngleUnit
        }

        val stripped = normalized
            .replace(Regex("""\b(deg(?:rees?)?|rad(?:ians?)?)\b""", RegexOption.IGNORE_CASE), "")
            .trim()

        return runCatching {
            val parser = SciParser(stripped, effectiveUnit)
            val value = parser.parseExpression()
            if (!parser.isAtEnd()) {
                throw IllegalArgumentException("Unexpected token near '${parser.remainingPreview()}'")
            }
            if (!value.isFinite()) {
                throw ArithmeticException("Result is undefined or infinite.")
            }
            if (abs(value) < 1e-12) 0.0 else value
        }
    }

    fun format(value: Double): String {
        val clean = if (abs(value) < 1e-12) 0.0 else value
        val roundedInt = clean.toLong()
        if (abs(clean - roundedInt.toDouble()) < 1e-10 && abs(clean) < 1e15) {
            return roundedInt.toString()
        }
        return DecimalFormat("#.########", DecimalFormatSymbols(Locale.US)).format(clean)
    }

    fun looksLikeExpression(text: String): Boolean {
        val lower = text.trim().lowercase(Locale.US)
        if (lower.isEmpty()) return false
        val hasFunction = FUNCTIONS.any { lower.contains("$it(") }
        val hasSciOp = lower.contains('^') || lower.contains('!') || lower.contains("√") || lower.contains("π")
        return hasFunction || hasSciOp
    }

    private class SciParser(
        private val input: String,
        private val angleUnit: AngleUnit
    ) {
        private var pos = 0

        fun isAtEnd(): Boolean {
            skipWhitespace()
            return pos >= input.length
        }

        fun remainingPreview(): String = input.substring(pos).take(12)

        fun parseExpression(): Double {
            var value = parseTerm()
            while (true) {
                skipWhitespace()
                value = when {
                    match('+') -> value + parseTerm()
                    match('-') -> value - parseTerm()
                    else -> return value
                }
            }
        }

        private fun parseTerm(): Double {
            var value = parsePower()
            while (true) {
                skipWhitespace()
                value = when {
                    match('*') -> value * parsePower()
                    match('/') -> {
                        val divisor = parsePower()
                        if (divisor == 0.0) throw ArithmeticException("Division by zero")
                        value / divisor
                    }
                    match('%') -> {
                        val divisor = parsePower()
                        if (divisor == 0.0) throw ArithmeticException("Modulo by zero")
                        value % divisor
                    }
                    else -> return value
                }
            }
        }

        private fun parsePower(): Double {
            val base = parseUnary()
            skipWhitespace()
            return if (match('^')) {
                val exponent = parsePower() // Right-associative
                base.pow(exponent)
            } else {
                base
            }
        }

        private fun parseUnary(): Double {
            skipWhitespace()
            if (match('+')) return parseUnary()
            if (match('-')) return -parseUnary()
            return parsePostfix()
        }

        private fun parsePostfix(): Double {
            var value = parsePrimary()
            while (true) {
                skipWhitespace()
                if (match('!')) {
                    value = factorial(value)
                } else {
                    return value
                }
            }
        }

        private fun parsePrimary(): Double {
            skipWhitespace()
            if (match('(')) {
                val inner = parseExpression()
                if (!match(')')) {
                    throw IllegalArgumentException("Missing closing parenthesis")
                }
                return inner
            }

            if (pos < input.length && (input[pos].isDigit() || input[pos] == '.')) {
                val start = pos
                while (pos < input.length && (input[pos].isDigit() || input[pos] == '.')) {
                    pos++
                }
                if (pos < input.length && (input[pos] == 'e' || input[pos] == 'E')) {
                    val expStart = pos
                    val nextIdx = pos + 1
                    if (nextIdx < input.length && (input[nextIdx].isDigit() || input[nextIdx] == '+' || input[nextIdx] == '-')) {
                        pos++
                        if (input[pos] == '+' || input[pos] == '-') pos++
                        while (pos < input.length && input[pos].isDigit()) pos++
                    } else {
                        pos = expStart
                    }
                }
                return input.substring(start, pos).toDouble()
            }

            if (pos < input.length && input[pos].isLetter()) {
                val start = pos
                while (pos < input.length && (input[pos].isLetterOrDigit() || input[pos] == '_')) {
                    pos++
                }
                val ident = input.substring(start, pos).lowercase(Locale.US)
                skipWhitespace()
                if (match('(')) {
                    val arg = parseExpression()
                    if (!match(')')) {
                        throw IllegalArgumentException("Missing ')' after $ident(")
                    }
                    return applyFunction(ident, arg)
                }
                return when (ident) {
                    "pi" -> PI
                    "tau" -> 2.0 * PI
                    "e" -> kotlin.math.E
                    else -> throw IllegalArgumentException("Unknown symbol '$ident'")
                }
            }

            throw IllegalArgumentException("Unexpected character at index $pos")
        }

        private fun applyFunction(name: String, arg: Double): Double {
            val rad = if (angleUnit == AngleUnit.DEG) Math.toRadians(arg) else arg
            return when (name) {
                "sin" -> sin(rad)
                "cos" -> cos(rad)
                "tan" -> {
                    val c = cos(rad)
                    if (abs(c) < 1e-12) throw ArithmeticException("tan is undefined at this angle")
                    tan(rad)
                }
                "asin" -> {
                    if (arg !in -1.0..1.0) throw ArithmeticException("asin domain is [-1, 1]")
                    val res = asin(arg)
                    if (angleUnit == AngleUnit.DEG) Math.toDegrees(res) else res
                }
                "acos" -> {
                    if (arg !in -1.0..1.0) throw ArithmeticException("acos domain is [-1, 1]")
                    val res = acos(arg)
                    if (angleUnit == AngleUnit.DEG) Math.toDegrees(res) else res
                }
                "atan" -> {
                    val res = atan(arg)
                    if (angleUnit == AngleUnit.DEG) Math.toDegrees(res) else res
                }
                "sinh" -> sinh(arg)
                "cosh" -> cosh(arg)
                "tanh" -> tanh(arg)
                "sqrt" -> {
                    if (arg < 0.0) throw ArithmeticException("Square root of negative number")
                    sqrt(arg)
                }
                "cbrt" -> cbrt(arg)
                "abs" -> abs(arg)
                "ln" -> {
                    if (arg <= 0.0) throw ArithmeticException("ln domain is (> 0)")
                    ln(arg)
                }
                "log", "log10" -> {
                    if (arg <= 0.0) throw ArithmeticException("log10 domain is (> 0)")
                    log10(arg)
                }
                "log2" -> {
                    if (arg <= 0.0) throw ArithmeticException("log2 domain is (> 0)")
                    log2(arg)
                }
                "exp" -> exp(arg)
                "floor" -> floor(arg)
                "ceil" -> ceil(arg)
                "round" -> round(arg)
                else -> throw IllegalArgumentException("Unknown function '$name'")
            }
        }

        private fun factorial(value: Double): Double {
            val n = value.toInt()
            if (value < 0.0 || abs(value - n.toDouble()) > 1e-9 || n > 20) {
                throw ArithmeticException("Factorial supports integers 0..20")
            }
            var acc = 1.0
            for (i in 2..n) {
                acc *= i.toDouble()
            }
            return acc
        }

        private fun match(expected: Char): Boolean {
            skipWhitespace()
            if (pos < input.length && input[pos] == expected) {
                pos++
                return true
            }
            return false
        }

        private fun skipWhitespace() {
            while (pos < input.length && input[pos].isWhitespace()) {
                pos++
            }
        }
    }

    private val FUNCTIONS = setOf(
        "sin", "cos", "tan", "asin", "acos", "atan",
        "sinh", "cosh", "tanh", "sqrt", "cbrt", "abs",
        "ln", "log", "log10", "log2", "exp", "floor", "ceil", "round"
    )
}
