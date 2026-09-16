package ai.localmind.core

data class InputSchema(
    val required: Set<String> = emptySet(),
    val types: Map<String, Class<*>> = emptyMap()
)

data class ToolResult(
    val success: Boolean,
    val data: Map<String, Any?> = emptyMap(),
    val errorCode: String? = null,
    val errorMessage: String? = null
)

interface Tool {
    val id: String
    val name: String
    val description: String
    val riskLevel: RiskLevel
    val inputSchema: InputSchema
    fun execute(arguments: Map<String, Any?>): ToolResult
}

class ToolRegistry(private val trace: EventTrace) {
    private val tools = linkedMapOf<String, Tool>()

    fun register(tool: Tool) {
        require(tool.id.isNotBlank()) { "Tool id is required" }
        require(tool.id !in tools) { "Duplicate tool id: ${tool.id}" }
        tools[tool.id] = tool
    }

    fun get(id: String): Tool = tools[id] ?: error("Unknown tool: $id")

    fun execute(id: String, arguments: Map<String, Any?>, confirmed: Boolean = false): ToolResult {
        val tool = get(id)
        if (tool.riskLevel == RiskLevel.BLOCKED) {
            return fail(id, "blocked_tool", "This operation is blocked by policy.")
        }
        if (tool.riskLevel == RiskLevel.CONFIRM && !confirmed) {
            return fail(id, "confirmation_required", "This operation needs user confirmation.")
        }

        validate(arguments, tool.inputSchema)?.let { return fail(id, "invalid_arguments", it) }

        trace.emit(
            "tool_started",
            tool = id,
            payload = mapOf("inputKeys" to arguments.keys.sorted(), "riskLevel" to tool.riskLevel.name)
        )
        val result = tool.execute(arguments)
        if (result.success) {
            trace.emit("tool_completed", tool = id, payload = mapOf("resultKeys" to result.data.keys.sorted()))
        } else {
            trace.emit("tool_failed", tool = id, message = result.errorMessage, payload = mapOf("code" to result.errorCode))
        }
        return result
    }

    private fun fail(toolId: String, code: String, message: String): ToolResult {
        trace.emit("tool_failed", tool = toolId, message = message, payload = mapOf("code" to code))
        return ToolResult(success = false, errorCode = code, errorMessage = message)
    }

    private fun validate(arguments: Map<String, Any?>, schema: InputSchema): String? {
        schema.required.forEach { key ->
            if (!arguments.containsKey(key)) return "Missing required argument: $key"
        }
        schema.types.forEach { (key, type) ->
            val value = arguments[key] ?: return@forEach
            if (!type.isInstance(value)) return "$key must be ${type.simpleName}"
        }
        return null
    }
}
