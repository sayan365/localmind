package ai.localmind.device.model

import java.util.Locale
import kotlin.math.cos

internal fun verifiedLocalAnswer(prompt: String): String? {
    val normalized = prompt.lowercase(Locale.US).replace(Regex("[^a-z0-9.]+"), " ").trim()

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

internal fun requiresReliableKnowledge(prompt: String): Boolean {
    val normalized = prompt.trim().lowercase(Locale.US)
    if (DIRECT_REQUEST_MARKERS.any(normalized::contains)) return false
    return KNOWLEDGE_PREFIXES.any(normalized::startsWith) ||
        KNOWLEDGE_MARKERS.any(normalized::contains)
}

private fun formatNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

private val DIRECT_REQUEST_MARKERS = setOf(
    "write ", "draft ", "rewrite ", "translate ", "message ", "email ", "plan my", "help me"
)
private val KNOWLEDGE_PREFIXES = setOf(
    "what is ", "what are ", "who is ", "who was ", "when ", "where ", "why ", "explain ",
    "define ", "calculate ", "solve ", "derivative ", "compare "
)
private val KNOWLEDGE_MARKERS = setOf(
    "national bird", "national animal", "capital of", "derivative of", "integral of"
)
