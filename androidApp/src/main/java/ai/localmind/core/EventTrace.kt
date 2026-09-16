package ai.localmind.core

import java.time.Instant

data class TraceEvent(
    val id: String,
    val type: String,
    val timestamp: String = Instant.now().toString(),
    val state: AgentState? = null,
    val previousState: AgentState? = null,
    val tool: String? = null,
    val message: String? = null,
    val payload: Map<String, Any?> = emptyMap()
)

class EventTrace {
    private val events = mutableListOf<TraceEvent>()
    private val listeners = mutableListOf<(List<TraceEvent>) -> Unit>()

    fun emit(
        type: String,
        state: AgentState? = null,
        previousState: AgentState? = null,
        tool: String? = null,
        message: String? = null,
        payload: Map<String, Any?> = emptyMap()
    ): TraceEvent {
        val event = TraceEvent(
            id = "$type-${events.size + 1}",
            type = type,
            state = state,
            previousState = previousState,
            tool = tool,
            message = message,
            payload = payload
        )
        events += event
        listeners.forEach { it(events.toList()) }
        return event
    }

    fun subscribe(listener: (List<TraceEvent>) -> Unit) {
        listeners += listener
    }

    fun list(): List<TraceEvent> = events.toList()
}
