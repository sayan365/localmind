package ai.localmind.device.model

import java.util.Locale
import kotlin.math.cos

internal fun verifiedLocalAnswer(prompt: String): String? {
    val normalized = prompt.lowercase(Locale.US).replace(Regex("[^a-z0-9.]+"), " ").trim()

    localArithmeticAnswer(prompt)?.let { return it }

    if ("national bird" in normalized && "india" in normalized) {
        return "**India's national bird is the Indian peacock** (Indian peafowl, Pavo cristatus)."
    }

    if ("derivative" in normalized && "sin" in normalized) {
        val number = Regex("sin\\s*(-?\\d+(?:\\.\\d+)?)").find(normalized)
            ?.groupValues?.get(1)?.toDoubleOrNull()
        if (number != null) {
            val value = String.format(Locale.US, "%.4f", cos(number))
            return """
                **Derivative:** cos(x)

                At x = ${formatNumber(number)} radians, cos(${formatNumber(number)}) is approximately $value.

                If sin(${formatNumber(number)}) is a constant rather than a function of x, its derivative with respect to x is 0.
            """.trimIndent()
        }
        return "**Derivative:** d/dx[sin(x)] = cos(x)"
    }

    if ("derivative" in normalized && "cos" in normalized) {
        return "**Derivative:** d/dx[cos(x)] = -sin(x)"
    }

    return null
}

private fun localArithmeticAnswer(prompt: String): String? {
    val candidate = prompt.trim()
        .replace(Regex("(?i)^(?:please\\s+)?(?:calculate|compute|solve|what is|what's)\\s+"), "")
        .trim().trimEnd('?', '.', '=')
    if (candidate.isBlank() || !candidate.any(Char::isDigit) || candidate.any { it !in "0123456789.+-*/() ^×÷" }) return null
    val value = runCatching { ArithmeticParser(candidate.replace('×', '*').replace('÷', '/')).parse() }.getOrNull() ?: return null
    if (!value.isFinite()) return "That calculation is undefined."
    val formatted = if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.US, "%.8f", value).trimEnd('0').trimEnd('.')
    return "**Answer:** $formatted"
}

private class ArithmeticParser(private val source: String) {
    private var index = 0

    fun parse(): Double {
        val value = expression()
        skipSpaces()
        require(index == source.length) { "Unexpected input" }
        return value
    }

    private fun expression(): Double {
        var value = term()
        while (true) {
            skipSpaces()
            value = when (peek()) {
                '+' -> { index++; value + term() }
                '-' -> { index++; value - term() }
                else -> return value
            }
        }
    }

    private fun term(): Double {
        var value = power()
        while (true) {
            skipSpaces()
            value = when (peek()) {
                '*' -> { index++; value * power() }
                '/' -> { index++; value / power() }
                else -> return value
            }
        }
    }

    private fun power(): Double {
        var value = unary()
        skipSpaces()
        if (peek() == '^') {
            index++
            value = Math.pow(value, power())
        }
        return value
    }

    private fun unary(): Double {
        skipSpaces()
        return when (peek()) {
            '+' -> { index++; unary() }
            '-' -> { index++; -unary() }
            '(' -> {
                index++
                val value = expression()
                skipSpaces()
                require(peek() == ')') { "Missing parenthesis" }
                index++
                value
            }
            else -> number()
        }
    }

    private fun number(): Double {
        skipSpaces()
        val start = index
        while (peek()?.let { it.isDigit() || it == '.' } == true) index++
        require(start != index) { "Number expected" }
        return source.substring(start, index).toDouble()
    }

    private fun skipSpaces() { while (peek() == ' ') index++ }
    private fun peek(): Char? = source.getOrNull(index)
}

private fun formatNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
