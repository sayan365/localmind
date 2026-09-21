package ai.localmind.device.model

internal object ResponseQualityGate {
    fun rejectionReason(prompt: String, response: String, previousAssistant: String?): String? {
        val clean = response.trim()
        if (clean.length < 2) return "empty response"
        if (MODEL_ARTIFACT.containsMatchIn(clean)) return "model template artifact"
        if (FALSE_ACTION_CLAIM.containsMatchIn(clean)) return "unverified action claim"
        if (hasRepeatedParagraph(clean)) return "repeated response"
        if (previousAssistant != null && !isExplicitFollowUp(prompt) && similarity(clean, previousAssistant) > 0.86) {
            return "repeated previous answer"
        }
        return null
    }

    fun retryPrompt(prompt: String): String = """
        Answer this current request only: $prompt

        Give a concise, self-contained answer. Do not continue an earlier topic, expose reasoning, repeat text, or claim that a phone action was completed.
    """.trimIndent()

    const val SAFE_FALLBACK = "I couldn't verify a reliable answer from the information available on this phone. Try rephrasing the question or provide the text you want me to work with."

    private fun hasRepeatedParagraph(text: String): Boolean {
        val paragraphs = text.split(Regex("\\n\\s*\\n")).map { it.trim().lowercase() }.filter { it.length >= 24 }
        return paragraphs.size != paragraphs.distinct().size
    }

    private fun similarity(left: String, right: String): Double {
        val leftWords = words(left)
        val rightWords = words(right)
        if (leftWords.isEmpty() || rightWords.isEmpty()) return 0.0
        return leftWords.intersect(rightWords).size.toDouble() / leftWords.union(rightWords).size
    }

    private fun words(text: String): Set<String> = Regex("[a-z0-9]+").findAll(text.lowercase())
        .map { it.value }
        .filter { it.length > 2 }
        .toSet()

    private fun isExplicitFollowUp(prompt: String): Boolean {
        val words = words(prompt)
        return words.any { it in setOf("this", "that", "same", "again", "continue", "rewrite", "shorter", "longer", "formal", "casual") }
    }

    private val MODEL_ARTIFACT = Regex("(?i)<\\|im_(?:start|end)\\|>|<think>|</think>|new_string:|rendered template|string does not start")
    private val FALSE_ACTION_CLAIM = Regex("(?i)\\b(?:i|localmind) (?:have )?(?:sent|deleted|purchased|paid|booked|opened|created|scheduled|turned)\\b")
}
