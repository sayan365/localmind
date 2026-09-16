package ai.localmind.core

data class AgentStep(
    val id: String,
    val tool: String,
    val arguments: Map<String, Any?> = emptyMap(),
    val argumentsFrom: String? = null,
    val requiresConfirmation: Boolean = false
)

data class AgentPlan(
    val intent: String,
    val needsClarification: Boolean,
    val steps: List<AgentStep>
)

interface ModelEngine {
    fun plan(input: String): AgentPlan
    fun generate(deadlines: List<Deadline>): String
}

class MockModelEngine : ModelEngine {
    override fun plan(input: String): AgentPlan {
        val normalized = input.lowercase()
        if (!normalized.contains("rahul") && !normalized.contains("admission")) {
            return AgentPlan("unknown", needsClarification = true, steps = emptyList())
        }

        return AgentPlan(
            intent = "admission_deadline_reminders",
            needsClarification = false,
            steps = listOf(
                AgentStep("search-rahul-email", "gmail.search_emails", mapOf("query" to "from:rahul admission", "maxResults" to 10)),
                AgentStep("read-rahul-email", "gmail.read_email", argumentsFrom = "search-rahul-email"),
                AgentStep("read-admission-document", "drive.read_document", argumentsFrom = "read-rahul-email"),
                AgentStep("create-calendar-events", "calendar.create_event", argumentsFrom = "read-admission-document", requiresConfirmation = true),
                AgentStep("verify-calendar-events", "calendar.verify_event", argumentsFrom = "create-calendar-events")
            )
        )
    }

    override fun generate(deadlines: List<Deadline>): String = summarizeDeadlines(deadlines)
}

fun summarizeDeadlines(deadlines: List<Deadline>): String {
    if (deadlines.isEmpty()) return "No admission deadlines were found."
    val lines = deadlines.joinToString("\n") { "${it.title}: ${it.date} (${it.reason})" }
    return "I found ${deadlines.size} admission milestones:\n$lines"
}
