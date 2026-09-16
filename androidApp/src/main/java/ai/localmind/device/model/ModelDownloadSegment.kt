package ai.localmind.device.model

data class ModelDownloadSegment(val index: Int, val startByte: Long, val endByteInclusive: Long) {
    val byteCount: Long = endByteInclusive - startByte + 1L
}

fun modelDownloadSegments(totalBytes: Long, chunkBytes: Long = MODEL_DOWNLOAD_CHUNK_BYTES): List<ModelDownloadSegment> {
    require(totalBytes > 0L)
    require(chunkBytes > 0L)
    val chunkCount = ((totalBytes + chunkBytes - 1L) / chunkBytes).toInt()
    return List(chunkCount) { index ->
        val start = index * chunkBytes
        ModelDownloadSegment(index, start, minOf(totalBytes - 1L, start + chunkBytes - 1L))
    }
}

const val MODEL_DOWNLOAD_WORKERS = 16
const val MODEL_DOWNLOAD_CHUNK_BYTES = 8L * 1024L * 1024L
const val MODEL_DOWNLOAD_LAYOUT_VERSION = 3
