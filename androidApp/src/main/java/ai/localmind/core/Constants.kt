package ai.localmind.core

enum class AgentState {
    IDLE,
    UNDERSTANDING,
    PLANNING,
    EXECUTING,
    OBSERVING,
    WAITING_FOR_CONFIRMATION,
    VERIFYING,
    COMPLETED,
    FAILED,
    CANCELLED
}

enum class RiskLevel {
    SAFE,
    CONFIRM,
    BLOCKED
}
