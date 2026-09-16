package ai.localmind.device.model

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

class LocalModelStore(private val context: Context) {
    private val preferences = context.getSharedPreferences("local_models", Context.MODE_PRIVATE)

    val modelsDirectory: File
        get() = File(context.getExternalFilesDir(null) ?: context.filesDir, "models").apply { mkdirs() }

    init {
        File(modelsDirectory, LEGACY_QWEN_17_FILE).delete()
        File(modelsDirectory, REPLACED_QWEN_FILE).delete()
        File(modelsDirectory, "$REPLACED_QWEN_FILE.downloading").delete()
        deletePartFiles(REPLACED_QWEN_FILE)
    }

    fun selected(recommended: LocalModelSpec): LocalModelSpec {
        val selectionVersion = preferences.getInt(KEY_SELECTION_VERSION, 1)
        val saved = LocalModelCatalog.byId(preferences.getString(KEY_SELECTED_MODEL, null))
        if (selectionVersion < CURRENT_SELECTION_VERSION) {
            val migrated = if (
                selectionVersion == GEMMA_AUTO_SELECTION_VERSION &&
                saved == LocalModelCatalog.standard &&
                !isAvailable(LocalModelCatalog.standard)
            ) LocalModelCatalog.lite else saved
            preferences.edit()
                .putInt(KEY_SELECTION_VERSION, CURRENT_SELECTION_VERSION)
                .putString(KEY_SELECTED_MODEL, (migrated ?: recommended).id)
                .apply()
            return migrated ?: recommended
        }
        return saved ?: recommended
    }

    fun select(model: LocalModelSpec) {
        preferences.edit()
            .putString(KEY_SELECTED_MODEL, model.id)
            .putInt(KEY_SELECTION_VERSION, CURRENT_SELECTION_VERSION)
            .apply()
    }

    fun file(model: LocalModelSpec): File = File(modelsDirectory, model.fileName)

    fun downloadFile(model: LocalModelSpec): File = File(modelsDirectory, "${model.fileName}.downloading")

    fun downloadPartFile(model: LocalModelSpec, index: Int): File = File(modelsDirectory, "${model.fileName}.part-$index")

    fun downloadedBytes(model: LocalModelSpec): Long = modelDownloadSegments(model.downloadBytes).sumOf { segment ->
        downloadPartFile(model, segment.index).length().coerceAtMost(segment.byteCount)
    }

    fun isAvailable(model: LocalModelSpec): Boolean {
        val modelFile = file(model)
        if (!modelFile.isFile || !model.hasCompleteFileSize(modelFile.length())) return false
        if (hasCompletionMarker(model)) return true
        if (model.sha256 == null) return false
        return verifySha256(modelFile, model.sha256).also { if (it) markComplete(model) }
    }

    fun activateDownload(model: LocalModelSpec): Boolean {
        val partial = downloadFile(model)
        if (!partial.isFile || !model.hasCompleteFileSize(partial.length())) {
            return false
        }
        if (model.sha256 != null && !verifySha256(partial, model.sha256)) return false
        val target = file(model)
        if (target.exists() && !target.delete()) return false
        if (!partial.renameTo(target)) return false
        markComplete(model)
        return true
    }

    fun activateImport(model: LocalModelSpec, importedFile: File): Boolean {
        if (!importedFile.isFile || !model.hasCompleteFileSize(importedFile.length())) return false
        if (model.sha256 != null && !verifySha256(importedFile, model.sha256)) return false
        val target = file(model)
        if (target.exists() && !target.delete()) return false
        if (!importedFile.renameTo(target)) return false
        markComplete(model)
        return true
    }

    fun deleteModel(model: LocalModelSpec) {
        file(model).delete()
        completionFile(model).delete()
    }

    fun deleteDownloadFiles(model: LocalModelSpec) {
        downloadFile(model).delete()
        deletePartFiles(model.fileName)
    }

    fun assembleDownload(model: LocalModelSpec, segments: List<ModelDownloadSegment>): Boolean = runCatching {
        val partial = downloadFile(model)
        FileOutputStream(partial, false).buffered().use { output ->
            segments.forEach { segment ->
                val part = downloadPartFile(model, segment.index)
                check(part.isFile && part.length() == segment.byteCount)
                part.inputStream().buffered().use { it.copyTo(output) }
            }
        }
        check(partial.length() == model.downloadBytes)
        segments.forEach { downloadPartFile(model, it.index).delete() }
        true
    }.getOrDefault(false)

    fun beginDownload(model: LocalModelSpec) {
        if (preferences.getInt(KEY_DOWNLOAD_LAYOUT_VERSION, -1) != MODEL_DOWNLOAD_LAYOUT_VERSION) deleteDownloadFiles(model)
        preferences.edit()
            .putString(KEY_ACTIVE_DOWNLOAD_MODEL, model.id)
            .putString(KEY_DOWNLOAD_STATE, DOWNLOAD_ACTIVE)
            .putInt(KEY_DOWNLOAD_LAYOUT_VERSION, MODEL_DOWNLOAD_LAYOUT_VERSION)
            .remove(KEY_DOWNLOAD_ERROR)
            .apply()
    }

    fun updateDownloadProgress(model: LocalModelSpec, bytes: Long) {
        if (preferences.getString(KEY_ACTIVE_DOWNLOAD_MODEL, null) == model.id) {
            preferences.edit().putLong(KEY_DOWNLOAD_BYTES, bytes).apply()
        }
    }

    fun markDownloadVerifying(model: LocalModelSpec) = setDownloadState(model, DOWNLOAD_VERIFYING)
    fun markDownloadComplete(model: LocalModelSpec) = setDownloadState(model, DOWNLOAD_COMPLETE)

    fun markDownloadFailed(model: LocalModelSpec, message: String) {
        preferences.edit()
            .putString(KEY_ACTIVE_DOWNLOAD_MODEL, model.id)
            .putString(KEY_DOWNLOAD_STATE, DOWNLOAD_FAILED)
            .putString(KEY_DOWNLOAD_ERROR, message)
            .apply()
    }

    fun downloadStatus(model: LocalModelSpec): ModelDownloadStatus? {
        if (preferences.getString(KEY_ACTIVE_DOWNLOAD_MODEL, null) != model.id) return null
        return ModelDownloadStatus(
            preferences.getString(KEY_DOWNLOAD_STATE, DOWNLOAD_ACTIVE) ?: DOWNLOAD_ACTIVE,
            downloadedBytes(model),
            preferences.getString(KEY_DOWNLOAD_ERROR, null)
        )
    }

    fun clearDownloadState() {
        preferences.edit()
            .remove(KEY_ACTIVE_DOWNLOAD_MODEL)
            .remove(KEY_DOWNLOAD_STATE)
            .remove(KEY_DOWNLOAD_BYTES)
            .remove(KEY_DOWNLOAD_ERROR)
            .remove(KEY_DOWNLOAD_LAYOUT_VERSION)
            .apply()
    }

    fun trackDownload(model: LocalModelSpec, id: Long) {
        preferences.edit().putString(KEY_DOWNLOAD_MODEL, model.id).putLong(KEY_DOWNLOAD_ID, id).apply()
    }

    fun trackedDownload(model: LocalModelSpec): Long? {
        if (preferences.getString(KEY_DOWNLOAD_MODEL, null) != model.id) return null
        return preferences.getLong(KEY_DOWNLOAD_ID, -1L).takeIf { it >= 0L }
    }

    fun clearTrackedDownload() {
        preferences.edit().remove(KEY_DOWNLOAD_MODEL).remove(KEY_DOWNLOAD_ID).apply()
    }

    private fun completionFile(model: LocalModelSpec): File = File(modelsDirectory, "${model.fileName}.complete")

    private fun deletePartFiles(fileName: String) {
        val prefix = "$fileName.part-"
        modelsDirectory.listFiles()?.filter { it.name.startsWith(prefix) }?.forEach(File::delete)
    }

    private fun hasCompletionMarker(model: LocalModelSpec): Boolean = completionFile(model).let {
        it.isFile && it.readText() == completionMarker(model)
    }

    private fun markComplete(model: LocalModelSpec) {
        completionFile(model).writeText(completionMarker(model))
    }

    private fun setDownloadState(model: LocalModelSpec, state: String) {
        preferences.edit()
            .putString(KEY_ACTIVE_DOWNLOAD_MODEL, model.id)
            .putString(KEY_DOWNLOAD_STATE, state)
            .apply()
    }

    private fun completionMarker(model: LocalModelSpec): String = "${model.downloadBytes}:${model.sha256.orEmpty()}"

    private fun verifySha256(file: File, expected: String): Boolean = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }.equals(expected, ignoreCase = true)
    }.getOrDefault(false)

    companion object {
        private const val KEY_SELECTED_MODEL = "selected_model"
        private const val KEY_SELECTION_VERSION = "selection_version"
        private const val KEY_DOWNLOAD_MODEL = "download_model"
        private const val KEY_DOWNLOAD_ID = "download_id"
        private const val KEY_ACTIVE_DOWNLOAD_MODEL = "active_download_model"
        private const val KEY_DOWNLOAD_STATE = "download_state"
        private const val KEY_DOWNLOAD_BYTES = "download_bytes"
        private const val KEY_DOWNLOAD_ERROR = "download_error"
        private const val KEY_DOWNLOAD_LAYOUT_VERSION = "download_layout_version"
        private const val GEMMA_AUTO_SELECTION_VERSION = 3
        private const val CURRENT_SELECTION_VERSION = 4
        private const val LEGACY_QWEN_17_FILE = "Qwen3-1.7B_dynamic_wi4b32_afp32.litertlm"
        private const val REPLACED_QWEN_FILE = "Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm"
        const val DOWNLOAD_ACTIVE = "active"
        const val DOWNLOAD_VERIFYING = "verifying"
        const val DOWNLOAD_COMPLETE = "complete"
        const val DOWNLOAD_FAILED = "failed"
    }
}

data class ModelDownloadStatus(val state: String, val downloadedBytes: Long, val error: String?)
