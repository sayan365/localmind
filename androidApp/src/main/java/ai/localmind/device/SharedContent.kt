package ai.localmind.device

import java.io.Reader

data class SharedContent(
    val id: String,
    val mimeType: String,
    val text: String?,
    val uri: String?,
    val receivedAt: Long
) {
    fun preview(maxLength: Int = 96): String {
        val source = text?.trim()?.replace(Regex("\\s+"), " ")
            ?: uri?.substringAfterLast('/')
            ?: "Shared item"
        if (source.length <= maxLength) return source
        if (maxLength <= 3) return source.take(maxLength.coerceAtLeast(0))
        return source.take(maxLength - 3) + "..."
    }
}

fun searchSharedContent(items: List<SharedContent>, query: String): List<SharedContent> {
    val terms = query.lowercase().split(Regex("\\s+")).filter { it.length >= 2 }.distinct()
    if (terms.isEmpty()) return emptyList()
    return items.mapNotNull { item ->
        val searchable = listOfNotNull(item.text, item.uri, item.mimeType).joinToString(" ").lowercase()
        val score = terms.count(searchable::contains)
        if (score == 0) null else item to score
    }.sortedWith(compareByDescending<Pair<SharedContent, Int>> { it.second }.thenByDescending { it.first.receivedAt })
        .map(Pair<SharedContent, Int>::first)
}

fun readBoundedText(reader: Reader, maxChars: Int): String {
    require(maxChars >= 0) { "maxChars must not be negative" }
    if (maxChars == 0) return ""

    val output = StringBuilder(minOf(maxChars, READ_BUFFER_CHARS))
    val buffer = CharArray(minOf(maxChars, READ_BUFFER_CHARS))
    while (output.length < maxChars) {
        val requested = minOf(buffer.size, maxChars - output.length)
        val count = reader.read(buffer, 0, requested)
        if (count < 0) break
        if (count == 0) continue
        output.append(buffer, 0, count)
    }
    return output.toString()
}

private const val READ_BUFFER_CHARS = 8 * 1024
