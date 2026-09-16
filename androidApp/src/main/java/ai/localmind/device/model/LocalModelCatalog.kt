package ai.localmind.device.model

data class LocalModelSpec(
    val id: String,
    val displayName: String,
    val fileName: String,
    val downloadUrl: String?,
    val downloadPageUrl: String? = null,
    val downloadBytes: Long,
    val sha256: String? = null,
    val minimumRamBytes: Long,
    val maxTokens: Int,
    val maxOutputTokens: Int,
    val useGpu: Boolean,
    val thinkingEnabled: Boolean
) {
    val downloadSizeLabel: String
        get() = if (downloadBytes >= GIB) "%.1f GB".format(downloadBytes.toDouble() / GIB) else "${downloadBytes / MIB} MB"

    fun hasCompleteFileSize(actualBytes: Long): Boolean = actualBytes == downloadBytes

    private companion object {
        const val MIB = 1024L * 1024L
        const val GIB = 1024L * MIB
    }
}

object LocalModelCatalog {
    private const val GIB = 1024L * 1024L * 1024L

    val lite = LocalModelSpec(
        id = "qwen3-0.6b-int4",
        displayName = "Qwen3 0.6B Lite",
        fileName = "qwen3_0.6b_q4_block32_ekv1280.litertlm",
        downloadUrl = "https://huggingface.co/litert-community/Qwen3-0.6B-int4/resolve/main/qwen3_0.6b_q4_block32_ekv1280.litertlm?download=true",
        downloadBytes = 347_251_840L,
        sha256 = "312ce4e6bc0816edf844b9b7e66542648a89440b54822c3581e48a19e9ff5715",
        minimumRamBytes = 4L * GIB,
        maxTokens = 1280,
        maxOutputTokens = 256,
        useGpu = false,
        thinkingEnabled = false
    )

    val standard = LocalModelSpec(
        id = "gemma-3-1b-it-int4",
        displayName = "Gemma 3 1B",
        fileName = "gemma3-1b-it-int4.litertlm",
        downloadUrl = null,
        downloadPageUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT",
        downloadBytes = 584_417_280L,
        minimumRamBytes = 13L * GIB / 4L,
        maxTokens = 2048,
        maxOutputTokens = 384,
        useGpu = false,
        thinkingEnabled = false
    )

    val full = LocalModelSpec(
        id = "gemma-4-e2b-it",
        displayName = "Gemma 4 E2B",
        fileName = "gemma-4-E2B-it.litertlm",
        downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm?download=true",
        downloadBytes = 2_588_147_712L,
        minimumRamBytes = 8L * GIB,
        maxTokens = 4096,
        maxOutputTokens = 384,
        useGpu = true,
        thinkingEnabled = false
    )

    // Preserve the old API name and model id for existing installations.
    val compact = lite

    val all = listOf(lite, standard, full)

    fun recommended(totalRamBytes: Long): LocalModelSpec = when {
        totalRamBytes >= full.minimumRamBytes -> full
        else -> lite
    }

    fun byId(id: String?): LocalModelSpec? = all.firstOrNull { it.id == id }
}
