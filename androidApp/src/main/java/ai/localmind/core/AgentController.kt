package ai.localmind.core

data class AgentRunResult(
    val state: AgentState,
    val summary: String,
    val needsConfirmation: Boolean = false,
    val proposedEvents: List<CalendarEvent> = emptyList(),
    val createdEvents: List<CalendarEvent> = emptyList(),
    val trace: List<TraceEvent> = emptyList()
)

class AgentController(
    private val modelEngine: ModelEngine,
    private val toolRegistry: ToolRegistry,
    private val trace: EventTrace,
    private val maxSteps: Int = 12
) {
    private val stateMachine = AgentStateMachine(trace)
    private var plan: AgentPlan? = null
    private val observations = linkedMapOf<String, ToolResult>()
    private var nextStepIndex = 0
    private var deadlines = emptyList<Deadline>()
    private var createdEvents = emptyList<CalendarEvent>()

    val state: AgentState
        get() = stateMachine.state

    fun start(input: String): AgentRunResult {
        observations.clear()
        nextStepIndex = 0
        deadlines = emptyList()
        createdEvents = emptyList()

        trace.emit("agent_started", message = input)
        stateMachine.transition(AgentState.UNDERSTANDING, "User request received")
        stateMachine.transition(AgentState.PLANNING, "Creating structured plan")
        trace.emit("planning_started", message = input)

        val createdPlan = modelEngine.plan(input)
        plan = createdPlan
        trace.emit("plan_created", payload = mapOf("intent" to createdPlan.intent, "steps" to createdPlan.steps.map { it.tool }))

        if (createdPlan.needsClarification || createdPlan.steps.isEmpty()) {
            return fail("I need a more specific admission-related request before I can act.")
        }

        stateMachine.transition(AgentState.EXECUTING, "Executing safe tools")
        return runPlan(confirmed = false)
    }

    fun approvePending(): AgentRunResult {
        trace.emit("confirmation_received", payload = mapOf("confirmed" to true))
        stateMachine.transition(AgentState.EXECUTING, "User confirmed calendar creation")
        return runPlan(confirmed = true)
    }

    fun cancel(): AgentRunResult {
        stateMachine.transition(AgentState.CANCELLED, "User cancelled pending action")
        trace.emit("agent_cancelled", message = "Calendar creation cancelled.")
        return AgentRunResult(
            state = state,
            summary = "Calendar creation cancelled. No reminders were created.",
            trace = trace.list()
        )
    }

    private fun runPlan(confirmed: Boolean): AgentRunResult {
        val steps = plan?.steps.orEmpty()
        var executed = 0

        for (index in nextStepIndex until steps.size) {
            if (executed >= maxSteps) {
                return fail("The agent stopped because it reached the maximum step count.")
            }

            val step = steps[index]
            val arguments = resolveArguments(step)

            if (step.requiresConfirmation && !confirmed) {
                @Suppress("UNCHECKED_CAST")
                val proposed = arguments["events"] as List<CalendarEvent>
                nextStepIndex = index
                stateMachine.transition(AgentState.WAITING_FOR_CONFIRMATION, "Calendar reminders require confirmation")
                trace.emit(
                    "confirmation_requested",
                    tool = step.tool,
                    message = "Create ${proposed.size} calendar reminders?",
                    payload = mapOf("events" to proposed.map { "${it.title} ${it.date}" })
                )
                return AgentRunResult(
                    state = state,
                    summary = modelEngine.generate(deadlines),
                    needsConfirmation = true,
                    proposedEvents = proposed,
                    trace = trace.list()
                )
            }

            val result = toolRegistry.execute(step.tool, arguments, confirmed = step.requiresConfirmation && confirmed)
            if (!result.success) {
                return fail(result.errorMessage ?: "Tool failed.")
            }

            observations[step.id] = result
            observe(step, result)
            stateMachine.transition(AgentState.OBSERVING, "Observed ${step.tool}")

            if (step.tool == "calendar.verify_event") {
                trace.emit("verification_started", tool = step.tool)
                val verified = result.data["verified"] == true
                trace.emit("verification_completed", tool = step.tool, payload = mapOf("verified" to verified))
                stateMachine.transition(AgentState.VERIFYING, "Checking calendar events")
                if (!verified) {
                    return fail("Calendar creation was requested, but I couldn't verify every event.")
                }
            }

            if (index < steps.lastIndex && state != AgentState.VERIFYING) {
                stateMachine.transition(AgentState.EXECUTING, "Continuing plan")
            }
            nextStepIndex = index + 1
            executed += 1
        }

        stateMachine.transition(AgentState.COMPLETED, "All planned work verified")
        val summary = "${modelEngine.generate(deadlines)}\n\nCreated and verified ${createdEvents.size} calendar reminders."
        trace.emit("agent_completed", message = summary)
        return AgentRunResult(state = state, summary = summary, createdEvents = createdEvents, trace = trace.list())
    }

    private fun resolveArguments(step: AgentStep): Map<String, Any?> {
        if (step.argumentsFrom == null) return step.arguments

        return when (step.id) {
            "read-rahul-email" -> {
                @Suppress("UNCHECKED_CAST")
                val results = observations["search-rahul-email"]?.data?.get("results") as? List<DemoEmail>
                mapOf("emailId" to (results?.firstOrNull()?.id ?: "missing"))
            }
            "read-admission-document" -> {
                val email = observations["read-rahul-email"]?.data?.get("email") as? DemoEmail
                mapOf("documentId" to (email?.driveDocumentId ?: "missing"))
            }
            "create-calendar-events" -> mapOf(
                "events" to deadlines.mapIndexed { index, deadline ->
                    CalendarEvent(
                        id = "cal_${deadline.date.replace("-", "")}_${index + 1}",
                        title = "Admission reminder: ${deadline.title}",
                        date = deadline.date,
                        description = deadline.reason
                    )
                }
            )
            "verify-calendar-events" -> mapOf("eventIds" to createdEvents.map { it.id })
            else -> emptyMap()
        }
    }

    private fun observe(step: AgentStep, result: ToolResult) {
        if (step.id == "read-admission-document") {
            @Suppress("UNCHECKED_CAST")
            deadlines = result.data["deadlines"] as List<Deadline>
        }
        if (step.id == "create-calendar-events") {
            @Suppress("UNCHECKED_CAST")
            createdEvents = result.data["events"] as List<CalendarEvent>
        }
    }

    private fun fail(message: String): AgentRunResult {
        stateMachine.transition(AgentState.FAILED, message)
        trace.emit("agent_failed", message = message)
        return AgentRunResult(state = state, summary = message, trace = trace.list())
    }
}
