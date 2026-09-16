package ai.localmind.ui

import android.graphics.Color
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.BackgroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan

object ResponseFormatter {
    private val inlinePattern = Regex("\\*\\*([^*]+)\\*\\*|`([^`]+)`")

    fun format(source: String): CharSequence {
        val output = SpannableStringBuilder()
        val lines = normalizeMathMarkup(source).trim().lines()
        var codeBlock = false
        lines.forEachIndexed { index, rawLine ->
            if (rawLine.trim().startsWith("```")) {
                codeBlock = !codeBlock
                return@forEachIndexed
            }
            val line = rawLine.trimEnd()
            val headingLevel = line.takeWhile { it == '#' }.length
                .takeIf { it in 1..3 && line.getOrNull(it) == ' ' }
            val displayLine = when {
                headingLevel != null -> line.drop(headingLevel + 1)
                line.startsWith("- ") || line.startsWith("* ") -> "\u2022 ${line.drop(2)}"
                else -> line
            }
            val lineStart = output.length
            if (codeBlock) appendCode(output, displayLine) else appendInline(output, displayLine)
            if (headingLevel != null && output.length > lineStart) {
                output.setSpan(StyleSpan(Typeface.BOLD), lineStart, output.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                output.setSpan(
                    RelativeSizeSpan(if (headingLevel == 1) 1.18f else 1.08f),
                    lineStart,
                    output.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            if (index < lines.lastIndex) output.append('\n')
        }
        return output
    }

    private fun appendInline(output: SpannableStringBuilder, line: String) {
        var cursor = 0
        inlinePattern.findAll(line).forEach { match ->
            output.append(line.substring(cursor, match.range.first))
            val content = match.groups[1]?.value ?: match.groups[2]?.value.orEmpty()
            val start = output.length
            output.append(content)
            val span = if (match.groups[1] != null) StyleSpan(Typeface.BOLD) else TypefaceSpan("monospace")
            output.setSpan(span, start, output.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (match.groups[2] != null) {
                output.setSpan(
                    BackgroundColorSpan(Color.rgb(236, 239, 237)),
                    start,
                    output.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            cursor = match.range.last + 1
        }
        output.append(line.substring(cursor))
    }

    private fun appendCode(output: SpannableStringBuilder, line: String) {
        val start = output.length
        output.append(line)
        output.setSpan(TypefaceSpan("monospace"), start, output.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        output.setSpan(
            BackgroundColorSpan(Color.rgb(236, 239, 237)),
            start,
            output.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }
}

internal fun normalizeMathMarkup(source: String): String {
    var result = source
        .replace("\\(", "")
        .replace("\\)", "")
        .replace("\\[", "")
        .replace("\\]", "")

    result = Regex("\\$([^$\\n]+)\\$").replace(result) { match ->
        val expression = match.groupValues[1]
        if (expression.any { it in "\\^_={}+*/" }) expression.trim() else match.value
    }
    result = Regex("\\\\frac\\{([^{}]+)\\}\\{([^{}]+)\\}")
        .replace(result) { "(${it.groupValues[1]})/(${it.groupValues[2]})" }

    val commands = mapOf(
        "\\\\sin" to "sin",
        "\\\\cos" to "cos",
        "\\\\tan" to "tan",
        "\\\\sqrt" to "sqrt",
        "\\\\times" to "\u00D7",
        "\\\\cdot" to "\u00B7",
        "\\\\pi" to "\u03C0",
        "\\\\approx" to "\u2248"
    )
    commands.forEach { (command, display) -> result = result.replace(command, display) }
    return result
        .replace("\\,", " ")
        .replace(Regex("\\\\([A-Za-z]+)"), "$1")
}
