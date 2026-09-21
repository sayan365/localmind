package ai.localmind.device.model

import ai.localmind.ui.normalizeMathMarkup
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalModelCatalogTest {
    @Test
    fun phonesBelowEightGigabytesUseDownloadableLiteModelByDefault() {
        assertEquals(LocalModelCatalog.lite, LocalModelCatalog.recommended(4L * GIB))
        assertEquals(LocalModelCatalog.lite, LocalModelCatalog.recommended(6L * GIB))
        assertEquals("qwen3_0.6b_q4_block32_ekv1280.litertlm", LocalModelCatalog.lite.fileName)
        assertEquals(347_251_840L, LocalModelCatalog.lite.downloadBytes)
        assertEquals("312ce4e6bc0816edf844b9b7e66542648a89440b54822c3581e48a19e9ff5715", LocalModelCatalog.lite.sha256)
        assertEquals(16, MODEL_DOWNLOAD_WORKERS)
        assertEquals(8L * 1024L * 1024L, MODEL_DOWNLOAD_CHUNK_BYTES)
    }

    @Test
    fun gemma3UsesCpuAndLicenseAwareImport() {
        assertEquals("gemma3-1b-it-int4.litertlm", LocalModelCatalog.standard.fileName)
        assertEquals(false, LocalModelCatalog.standard.useGpu)
        assertEquals(null, LocalModelCatalog.standard.downloadUrl)
        assertEquals(true, LocalModelCatalog.standard.downloadPageUrl!!.contains("Gemma3-1B-IT"))
        assertEquals(false, LocalModelCatalog.all.any { it.id.contains("1.7b", ignoreCase = true) })
    }

    @Test
    fun lowMemoryDevicesUseLiteModel() {
        assertEquals(LocalModelCatalog.lite, LocalModelCatalog.recommended(3L * GIB))
    }

    @Test
    fun eightGigabyteDevicesUseFullModel() {
        assertEquals(LocalModelCatalog.full, LocalModelCatalog.recommended(8L * GIB))
    }

    @Test
    fun modelFileMustBeCloseToItsExpectedDownloadSize() {
        val expected = LocalModelCatalog.standard.downloadBytes
        assertEquals(true, LocalModelCatalog.standard.hasCompleteFileSize(expected))
        assertEquals(false, LocalModelCatalog.standard.hasCompleteFileSize(expected - 1L))
        assertEquals(false, LocalModelCatalog.standard.hasCompleteFileSize(expected + 1L))
        assertEquals(false, LocalModelCatalog.standard.hasCompleteFileSize(expected / 2L))
    }

    @Test
    fun downloadSegmentsCoverTheWholeModelWithoutOverlap() {
        val segments = modelDownloadSegments(101L, 16L)
        assertEquals(7, segments.size)
        assertEquals(0L, segments.first().startByte)
        assertEquals(100L, segments.last().endByteInclusive)
        assertEquals(101L, segments.sumOf { it.byteCount })
        segments.zipWithNext().forEach { (left, right) -> assertEquals(left.endByteInclusive + 1L, right.startByte) }
    }

    @Test
    fun thinkingTraceIsNotShownToTheUser() {
        assertEquals("Final answer", "<think>private reasoning</think>\nFinal answer<|im_end|>".cleanModelResponse())
        assertEquals("", "<think>unfinished private reasoning".cleanModelResponse())
    }

    @Test
    fun followUpPromptCarriesSanitizedConversationContext() {
        val prompt = buildConversationPrompt(
            listOf(ChatTurn("User", "Help me write a leave request"), ChatTurn("LocalMind", "Who should receive it?")),
            "Send it to HR"
        )

        assertEquals(true, prompt.contains("LocalMind: Who should receive it?"))
        assertEquals(true, prompt.contains("Current user request: Send it to HR"))
        assertEquals(false, prompt.contains("<|im_start|>"))
    }

    @Test
    fun unrelatedTopicStartsWithCleanContext() {
        val history = listOf(
            ChatTurn("User", "Write a leave message for HR"),
            ChatTurn("LocalMind", "Here is a leave request draft")
        )

        assertEquals("What is the national bird of India?", buildConversationPrompt(history, "What is the national bird of India?"))
        assertEquals("What is my next class?", buildConversationPrompt(history, "What is my next class?"))
        assertEquals(true, buildConversationPrompt(history, "Make it more formal").contains("leave message"))
    }

    @Test
    fun reasoningIsReservedForQuestionsRatherThanDrafting() {
        assertEquals(true, shouldUseThinking("What is the national bird of India?"))
        assertEquals(true, shouldUseThinking("Calculate 12 * 9"))
        assertEquals(false, shouldUseThinking("Write a leave request for HR"))
    }

    @Test
    fun verifiedAnswersHandleReportedFactAndDerivativeFailures() {
        assertEquals(
            "**India's national bird is the Indian peacock** (Indian peafowl, Pavo cristatus).",
            verifiedLocalAnswer("National bird of India")
        )
        assertEquals(true, verifiedLocalAnswer("derivative of sin 10")!!.contains("cos(10) is approximately -0.8391"))
        assertEquals("**Answer:** 14", verifiedLocalAnswer("calculate 2 + 3 * 4"))
        assertEquals("**Answer:** 20", verifiedLocalAnswer("What is (2 + 3) * 4?"))
    }

    @Test
    fun latexMathIsNormalizedForNativeAndroidText() {
        assertEquals(
            "The derivative of sin(10) is cos(10).",
            normalizeMathMarkup("The derivative of $ \\sin(10) $ is $ \\cos(10) $.")
        )
    }

    private companion object {
        const val GIB = 1024L * 1024L * 1024L
    }
}
