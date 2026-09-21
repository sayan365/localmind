package ai.localmind.device.model

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

interface OnDeviceChatEngine : AutoCloseable {
    fun initialize(onReady: () -> Unit, onError: (Throwable) -> Unit)
    fun send(prompt: String, onResponse: (String) -> Unit, onError: (Throwable) -> Unit)
    fun reset()
    fun cancel()
}

class LiteRtChatEngine(
    private val model: LocalModelSpec,
    private val modelFile: File,
    private val cacheDirectory: File
) : OnDeviceChatEngine {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private var engine: Engine? = null
    @Volatile private var activeConversation: Conversation? = null
    private val history = mutableListOf<ChatTurn>()

    override fun initialize(onReady: () -> Unit, onError: (Throwable) -> Unit) {
        executor.execute {
            runCatching {
                cacheDirectory.mkdirs()
                val runtime = Engine(
                    EngineConfig(
                        modelPath = modelFile.absolutePath,
                        backend = if (model.useGpu) Backend.GPU() else Backend.CPU(),
                        maxNumTokens = model.maxTokens,
                        cacheDir = cacheDirectory.absolutePath
                    )
                )
                engine = runtime
                runtime.initialize()
            }.onSuccess { onReady() }.onFailure {
                runCatching { engine?.close() }
                engine = null
                onError(it)
            }
        }
    }

    override fun send(prompt: String, onResponse: (String) -> Unit, onError: (Throwable) -> Unit) {
        executor.execute {
            runCatching {
                val runtime = checkNotNull(engine) { "The local model is not ready." }
                var answer = verifiedLocalAnswer(prompt).orEmpty()
                if (answer.isBlank()) {
                    val renderedPrompt = buildConversationPrompt(history, prompt)
                    val reasoning = shouldUseThinking(prompt)
                    answer = runTurn(runtime, renderedPrompt, reasoning)
                    if (answer.isBlank() && reasoning) answer = runTurn(runtime, renderedPrompt, false)
                    val previousAssistant = history.lastOrNull { it.speaker == "LocalMind" }?.text
                    if (ResponseQualityGate.rejectionReason(prompt, answer, previousAssistant) != null) {
                        answer = runTurn(runtime, ResponseQualityGate.retryPrompt(prompt), false)
                    }
                    if (ResponseQualityGate.rejectionReason(prompt, answer, previousAssistant) != null) {
                        answer = ResponseQualityGate.SAFE_FALLBACK
                    }
                }
                answer.ifBlank { "I could not produce a response. Please try rephrasing that." }
                    .also {
                        history += ChatTurn("User", prompt)
                        history += ChatTurn("LocalMind", it)
                        trimHistory(history)
                    }
            }.onSuccess(onResponse).onFailure(onError)
        }
    }

    private fun runTurn(runtime: Engine, prompt: String, reasoning: Boolean): String {
        val chat = runtime.createConversation(conversationConfig())
        activeConversation = chat
        return try {
            val response = chat.sendMessage(
                prompt,
                maxOutputToken = if (reasoning) REASONING_OUTPUT_TOKENS else model.maxOutputTokens,
                thinkingConfig = ThinkingConfig(enableThinking = reasoning, thinkingTokenBudget = THINKING_TOKEN_BUDGET)
            )
            response.contents.contents
                .filterIsInstance<Content.Text>()
                .joinToString(separator = "") { it.text }
                .cleanModelResponse()
        } finally {
            activeConversation = null
            chat.close()
        }
    }

    override fun cancel() {
        activeConversation?.cancelProcess()
    }

    override fun reset() {
        executor.execute { history.clear() }
    }

    override fun close() {
        runCatching { activeConversation?.close() }
        runCatching { engine?.close() }
        executor.shutdownNow()
        activeConversation = null
        engine = null
    }

    private fun conversationConfig() = ConversationConfig(
        systemInstruction = Contents.of(SYSTEM_INSTRUCTION),
        samplerConfig = SamplerConfig(topK = 20, topP = 0.85, temperature = 0.25, seed = 24),
        maxOutputToken = model.maxOutputTokens,
        thinkingConfig = ThinkingConfig(enableThinking = model.thinkingEnabled, thinkingTokenBudget = 128)
    )

    companion object {
        private const val SYSTEM_INSTRUCTION = """
            You are LocalMind, a practical and accurate assistant running privately on this Android device.
            Answer the current request directly. If the topic changes, do not repeat or continue the previous answer.
            Your real capabilities are: local chat and drafting; deterministic phone controls; confirmed WhatsApp and email drafts; calendar events; reminders; explicit memories; imported text search; and on-request SMS insights for unread counts, recent OTPs, transactions, balances mentioned in SMS, and spending totals.
            You cannot read WhatsApp history, obtain live bank balances, automatically send messages, browse the web, or use cloud AI. Never invent access to phone data.
            If you are uncertain about a factual answer, say so briefly instead of guessing. Check that the answer addresses the current question before finishing.
            When asked to write or draft something, produce a useful draft immediately from the details available. Do not ask the user to repeat details they already gave.
            Avoid canned phrases such as "let me help" and "let me know if you need more help". Use concise Markdown when lists or code improve readability.
            Write mathematical notation as readable plain text. Do not use LaTeX delimiters or commands.
            Never claim you opened an app, sent a message, changed a calendar, or completed an external action.
            When an action is requested, explain the proposed action clearly; the Android app will handle permissions and user confirmation.
            Do not reveal hidden reasoning. Answer directly in the user's language when practical.
        """

    }
}

internal data class ChatTurn(val speaker: String, val text: String)

internal fun buildConversationPrompt(history: List<ChatTurn>, prompt: String): String {
    val recentHistory = relevantHistory(history, prompt)
    if (recentHistory.isEmpty()) return prompt
    return buildString {
        appendLine("Previous exchange for context:")
        recentHistory.forEach { appendLine("${it.speaker}: ${it.text}") }
        appendLine("Current user request: $prompt")
        append("Answer the current request directly. Do not repeat the previous answer.")
    }
}

internal fun relevantHistory(history: List<ChatTurn>, prompt: String): List<ChatTurn> {
    val previousUserIndex = history.indexOfLast { it.speaker == "User" }
    if (previousUserIndex < 0) return emptyList()
    val previousUser = history[previousUserIndex].text
    if (!looksLikeFollowUp(previousUser, prompt)) return emptyList()
    return history.subList(previousUserIndex, history.size).takeLast(2)
}

internal fun looksLikeFollowUp(previous: String, current: String): Boolean {
    val allCurrentWords = WORD.findAll(current.lowercase()).map { it.value }.toSet()
    if (allCurrentWords.any(FOLLOW_UP_WORDS::contains)) return true
    val currentWords = meaningfulWords(current)
    val previousWords = meaningfulWords(previous)
    return currentWords.intersect(previousWords).isNotEmpty()
}

private fun meaningfulWords(text: String): Set<String> = WORD.findAll(text.lowercase())
    .map { it.value }
    .filter { it.length > 2 && it !in STOP_WORDS }
    .toSet()

internal fun trimHistory(history: MutableList<ChatTurn>) {
    while (history.sumOf { it.text.length } > MAX_HISTORY_CHARS && history.size > 2) {
        history.removeAt(0)
        history.removeAt(0)
    }
}

internal fun String.cleanModelResponse(): String =
    discardThinkingTrace()
        .replace(Regex("<\\|[^|>]+\\|>"), "")
        .trim()

private fun String.discardThinkingTrace(): String {
    val start = indexOf("<think>")
    if (start < 0) return this
    val end = indexOf("</think>", startIndex = start + 7)
    return if (end < 0) substring(0, start) else removeRange(start, end + 8)
}

private const val MAX_HISTORY_CHARS = 2200
private const val REASONING_OUTPUT_TOKENS = 512
private const val THINKING_TOKEN_BUDGET = 192
private val WORD = Regex("[a-z0-9]+")
private val FOLLOW_UP_WORDS = setOf(
    "it", "this", "that", "these", "those", "them", "same", "again", "continue",
    "rewrite", "shorter", "longer", "formal", "casual", "translate"
)
private val STOP_WORDS = setOf(
    "the", "and", "for", "with", "from", "please", "help", "write", "message", "tell", "about",
    "need", "want", "make", "give", "can", "you", "today"
)

internal fun shouldUseThinking(prompt: String): Boolean {
    val normalized = prompt.trim().lowercase()
    if (DIRECT_TASK_WORDS.any { normalized.contains(it) }) return false
    return normalized.endsWith("?") || REASONING_PREFIXES.any { normalized.startsWith(it) }
}

private val DIRECT_TASK_WORDS = setOf("write ", "draft ", "message ", "email ", "letter ", "rewrite ", "translate ")
private val REASONING_PREFIXES = setOf(
    "what ", "who ", "why ", "when ", "where ", "how ", "explain ", "compare ", "calculate ", "solve ", "derivative ", "summarize "
)
