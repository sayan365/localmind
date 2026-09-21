package ai.localmind.assistant

object CapabilityRegistry {
    fun isCapabilityQuestion(input: String): Boolean {
        val text = input.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
        return text in setOf(
            "what can you do",
            "what can you help me with",
            "how can you help me",
            "what are your capabilities",
            "show your capabilities",
            "help"
        )
    }

    fun userFacingSummary(): String = """
        **I can help on this phone with:**

        - Drafting, rewriting, summarizing, and basic questions with the local model
        - Reading SMS on request for unread counts, OTPs, transactions, balances mentioned in messages, and monthly spending
        - Opening supported apps and controlling the flashlight, media, and volume
        - Preparing WhatsApp messages and email drafts for your review
        - Creating confirmed calendar events and local reminders
        - Saving explicit memories and searching imported text

        I do not silently read your messages, send communications, or use cloud AI. WhatsApp history and live bank balances are not available.
    """.trimIndent()
}
