package ai.localmind.localdata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MemoryRankingTest {
    @Test
    fun normalizationMakesExplicitMemoriesSearchable() {
        assertEquals("my passport expires in 2028", normalizeMemory("My passport expires in 2028!"))
    }

    @Test
    fun rankingPrefersMoreMatchingTermsThenRecentUpdates() {
        val memories = listOf(
            memory(1, "Passport expires in 2028", 10),
            memory(2, "Passport is in the blue drawer", 30),
            memory(3, "The blue passport folder is locked", 20)
        )

        assertEquals(listOf(2L, 3L, 1L), rankMemories(memories, "blue passport").map { it.id })
    }

    @Test
    fun memorySizeLimitIsSuitableForBoundedLocalContext() {
        assertEquals("a compact memory", sanitizeMemoryContent("  a   compact\nmemory  "))
        assertThrows(IllegalArgumentException::class.java) {
            sanitizeMemoryContent("x".repeat(LocalDataRepository.MAX_MEMORY_CHARS + 1))
        }
    }

    private fun memory(id: Long, content: String, updatedAt: Long) = MemoryRecord(
        id = id,
        content = content,
        normalizedContent = normalizeMemory(content),
        createdAt = 0,
        updatedAt = updatedAt
    )
}
