package ai.localmind.core.tools

import ai.localmind.core.CalendarEvent
import ai.localmind.core.Deadline
import ai.localmind.core.DemoStore
import ai.localmind.core.InputSchema
import ai.localmind.core.RiskLevel
import ai.localmind.core.Tool
import ai.localmind.core.ToolResult

fun createMockKnowledgeTools(store: DemoStore): List<Tool> = listOf(
    object : Tool {
        override val id = "gmail.search_emails"
        override val name = "Search Gmail"
        override val description = "Search synthetic Gmail messages."
        override val riskLevel = RiskLevel.SAFE
        override val inputSchema = InputSchema(required = setOf("query", "maxResults"), types = mapOf("query" to String::class.java, "maxResults" to Int::class.javaObjectType))

        override fun execute(arguments: Map<String, Any?>): ToolResult {
            val query = (arguments["query"] as String).lowercase().replace("from:", "")
            val maxResults = arguments["maxResults"] as Int
            val results = store.emails.filter {
                "${it.from} ${it.subject} ${it.body}".lowercase().contains("rahul") ||
                    "${it.from} ${it.subject} ${it.body}".lowercase().contains(query)
            }.take(maxResults)
            return ToolResult(true, mapOf("results" to results))
        }
    },
    object : Tool {
        override val id = "gmail.read_email"
        override val name = "Read Gmail message"
        override val description = "Read one synthetic Gmail message."
        override val riskLevel = RiskLevel.SAFE
        override val inputSchema = InputSchema(required = setOf("emailId"), types = mapOf("emailId" to String::class.java))

        override fun execute(arguments: Map<String, Any?>): ToolResult {
            val email = store.emails.find { it.id == arguments["emailId"] }
                ?: return ToolResult(false, errorCode = "not_found", errorMessage = "Email was not found.")
            return ToolResult(true, mapOf("email" to email))
        }
    },
    object : Tool {
        override val id = "drive.read_document"
        override val name = "Read Drive document"
        override val description = "Read one synthetic Google Drive document."
        override val riskLevel = RiskLevel.SAFE
        override val inputSchema = InputSchema(required = setOf("documentId"), types = mapOf("documentId" to String::class.java))

        override fun execute(arguments: Map<String, Any?>): ToolResult {
            val document = store.documents.find { it.id == arguments["documentId"] }
                ?: return ToolResult(false, errorCode = "not_found", errorMessage = "Drive document was not found.")
            return ToolResult(true, mapOf("document" to document, "deadlines" to extractDeadlines(document.body)))
        }
    }
)

fun createMockGoogleTools(store: DemoStore): List<Tool> = createMockKnowledgeTools(store) + listOf(
    object : Tool {
        override val id = "calendar.create_event"
        override val name = "Create Calendar event"
        override val description = "Create synthetic Google Calendar events."
        override val riskLevel = RiskLevel.CONFIRM
        override val inputSchema = InputSchema(required = setOf("events"), types = mapOf("events" to List::class.java))

        override fun execute(arguments: Map<String, Any?>): ToolResult {
            @Suppress("UNCHECKED_CAST")
            val events = arguments["events"] as List<CalendarEvent>
            store.calendarEvents += events
            return ToolResult(true, mapOf("events" to events))
        }
    },
    object : Tool {
        override val id = "calendar.verify_event"
        override val name = "Verify Calendar event"
        override val description = "Verify synthetic Google Calendar events exist."
        override val riskLevel = RiskLevel.SAFE
        override val inputSchema = InputSchema(required = setOf("eventIds"), types = mapOf("eventIds" to List::class.java))

        override fun execute(arguments: Map<String, Any?>): ToolResult {
            @Suppress("UNCHECKED_CAST")
            val eventIds = arguments["eventIds"] as List<String>
            val found = store.calendarEvents.map { it.id }.toSet()
            val missing = eventIds.filterNot { it in found }
            return ToolResult(true, mapOf("verified" to missing.isEmpty(), "checked" to eventIds.size, "missing" to missing))
        }
    }
)

fun extractDeadlines(text: String): List<Deadline> {
    val rows = listOf(
        Triple("Application deadline", "Submit the application", Regex("Application deadline:\\s*([A-Za-z]+\\s+\\d{1,2},\\s+\\d{4})", RegexOption.IGNORE_CASE)),
        Triple("Document verification", "Complete document verification", Regex("Document verification:\\s*([A-Za-z]+\\s+\\d{1,2},\\s+\\d{4})", RegexOption.IGNORE_CASE)),
        Triple("Admission confirmation", "Confirm admission decision", Regex("Admission confirmation:\\s*([A-Za-z]+\\s+\\d{1,2},\\s+\\d{4})", RegexOption.IGNORE_CASE))
    )

    return rows.mapNotNull { (title, reason, regex) ->
        val dateText = regex.find(text)?.groupValues?.get(1) ?: return@mapNotNull null
        Deadline(title = title, date = toIsoDate(dateText), reason = reason)
    }
}

private fun toIsoDate(value: String): String {
    val months = mapOf(
        "january" to "01",
        "february" to "02",
        "march" to "03",
        "april" to "04",
        "may" to "05",
        "june" to "06",
        "july" to "07",
        "august" to "08",
        "september" to "09",
        "october" to "10",
        "november" to "11",
        "december" to "12"
    )
    val parts = Regex("^([A-Za-z]+)\\s+(\\d{1,2}),\\s+(\\d{4})$").find(value)?.groupValues ?: return value
    return "${parts[3]}-${months.getValue(parts[1].lowercase())}-${parts[2].padStart(2, '0')}"
}
