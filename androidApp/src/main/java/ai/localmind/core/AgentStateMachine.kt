package ai.localmind.core

class AgentStateMachine(private val trace: EventTrace) {
    var state: AgentState = AgentState.IDLE
        private set

    private val transitions = mapOf(
        AgentState.IDLE to setOf(AgentState.UNDERSTANDING, AgentState.CANCELLED),
        AgentState.UNDERSTANDING to setOf(AgentState.PLANNING, AgentState.FAILED, AgentState.CANCELLED),
        AgentState.PLANNING to setOf(AgentState.EXECUTING, AgentState.FAILED, AgentState.CANCELLED),
        AgentState.EXECUTING to setOf(AgentState.OBSERVING, AgentState.WAITING_FOR_CONFIRMATION, AgentState.VERIFYING, AgentState.FAILED, AgentState.CANCELLED),
        AgentState.OBSERVING to setOf(AgentState.EXECUTING, AgentState.WAITING_FOR_CONFIRMATION, AgentState.VERIFYING, AgentState.FAILED, AgentState.CANCELLED),
        AgentState.WAITING_FOR_CONFIRMATION to setOf(AgentState.EXECUTING, AgentState.CANCELLED, AgentState.FAILED),
        AgentState.VERIFYING to setOf(AgentState.COMPLETED, AgentState.FAILED, AgentState.CANCELLED),
        AgentState.COMPLETED to setOf(AgentState.IDLE),
        AgentState.FAILED to setOf(AgentState.IDLE),
        AgentState.CANCELLED to setOf(AgentState.IDLE)
    )

    fun transition(nextState: AgentState, reason: String) {
        val allowed = transitions[state].orEmpty()
        require(nextState in allowed) { "Invalid transition from $state to $nextState" }
        val previous = state
        state = nextState
        trace.emit(
            type = "state_changed",
            state = nextState,
            previousState = previous,
            message = reason
        )
    }
}
