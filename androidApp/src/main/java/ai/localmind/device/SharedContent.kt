package ai.localmind.device

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
