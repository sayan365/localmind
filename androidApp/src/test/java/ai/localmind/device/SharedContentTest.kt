package ai.localmind.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedContentTest {
    @Test
    fun previewCompactsWhitespaceAndBoundsPrivateContent() {
        val item = SharedContent("1", "text/plain", "Rahul:   The deadline\nis September 30", null, 0)
        assertEquals("Rahul: The deadline is September 30", item.preview())
        assertTrue(item.preview(12).length <= 12)
    }

    @Test
    fun previewUsesFileNameWhenThereIsNoText() {
        val item = SharedContent("1", "application/pdf", null, "content://provider/docs/admission.pdf", 0)
        assertEquals("admission.pdf", item.preview())
    }

    @Test
    fun localSearchRanksMatchingPrivateItems() {
        val items = listOf(
            SharedContent("1", "text/plain", "Rahul admission deadline September 30", null, 1),
            SharedContent("2", "application/pdf", null, "content://docs/budget.pdf", 2)
        )
        assertEquals(listOf("1"), searchSharedContent(items, "admission deadline").map { it.id })
        assertEquals(emptyList<SharedContent>(), searchSharedContent(items, "flight ticket"))
    }
}
