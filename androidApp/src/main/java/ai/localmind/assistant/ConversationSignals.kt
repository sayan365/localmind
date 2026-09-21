package ai.localmind.assistant

object ConversationSignals {
    fun localResponse(input: String): String? {
        val text = input.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
        return when {
            text in THANKS -> "You're welcome."
            text in ACKNOWLEDGEMENTS -> "Got it."
            text in POSITIVE_FEEDBACK -> "Glad that helped."
            text in GREETINGS -> "Hi. What can I help you with on this phone?"
            text in GOODBYES -> "See you."
            else -> null
        }
    }

    private val ACKNOWLEDGEMENTS = setOf(
        "ok", "okay", "alright", "all right", "got it", "understood", "i understand", "noted", "fine"
    )
    private val THANKS = setOf("thanks", "thank you", "thankyou", "thx", "thanks a lot")
    private val POSITIVE_FEEDBACK = setOf("great", "perfect", "nice", "good", "cool", "awesome", "that works")
    private val GREETINGS = setOf("hi", "hello", "hey", "good morning", "good afternoon", "good evening")
    private val GOODBYES = setOf("bye", "goodbye", "see you", "see you later")
}
