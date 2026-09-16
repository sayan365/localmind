package ai.localmind.localdata

import android.content.Context
import java.util.Locale

class LocalDataRepository(context: Context) {
    private val database = LocalDataDatabase.get(context)

    fun saveMemory(content: String, now: Long = System.currentTimeMillis()): MemoryRecord {
        val clean = sanitizeMemoryContent(content)
        val normalized = normalizeMemory(clean)
        val existing = database.memories().findExact(normalized)
        if (existing != null) {
            val updated = existing.copy(content = clean, updatedAt = now)
            database.memories().update(updated)
            return updated
        }
        val record = MemoryRecord(content = clean, normalizedContent = normalized, createdAt = now, updatedAt = now)
        return record.copy(id = database.memories().insert(record))
    }

    fun updateMemory(id: Long, content: String, now: Long = System.currentTimeMillis()): MemoryRecord {
        val clean = sanitizeMemoryContent(content)
        val existing = database.memories().listAll().firstOrNull { it.id == id }
            ?: error("Memory was not found.")
        return existing.copy(content = clean, normalizedContent = normalizeMemory(clean), updatedAt = now)
            .also(database.memories()::update)
    }

    fun listMemories(): List<MemoryRecord> = database.memories().listAll()

    fun searchMemories(query: String): List<MemoryRecord> = rankMemories(listMemories(), query)

    fun deleteMemory(id: Long): Boolean = database.memories().delete(id) > 0

    fun clearMemories() = database.memories().clear()

    fun addReminder(title: String, triggerAt: Long, now: Long = System.currentTimeMillis()): ReminderRecord {
        require(triggerAt > now) { "Reminder time must be in the future." }
        val clean = title.trim().replace(Regex("\\s+"), " ")
        require(clean.isNotBlank()) { "Reminder title is required." }
        require(clean.length <= MAX_REMINDER_TITLE_CHARS) { "Reminder title is too long." }
        val record = ReminderRecord(title = clean, triggerAt = triggerAt, createdAt = now)
        return record.copy(id = database.reminders().insert(record))
    }

    fun scheduledReminders(): List<ReminderRecord> = database.reminders().listScheduled()

    fun findReminder(id: Long): ReminderRecord? = database.reminders().find(id)

    fun markReminderDelivered(id: Long) {
        database.reminders().updateStatus(id, ReminderRecord.STATUS_DELIVERED)
    }

    fun expirePastReminders(now: Long = System.currentTimeMillis()) {
        database.reminders().expirePast(now, ReminderRecord.STATUS_EXPIRED)
    }

    fun deleteReminder(id: Long): Boolean = database.reminders().delete(id) > 0

    fun clearReminders() = database.reminders().clear()

    companion object {
        const val MAX_MEMORY_CHARS = 4_000
        const val MAX_REMINDER_TITLE_CHARS = 500
    }
}

internal fun normalizeMemory(value: String): String = value.lowercase(Locale.US)
    .replace(Regex("[^a-z0-9]+"), " ")
    .trim()

internal fun sanitizeMemoryContent(value: String): String = value.trim().replace(Regex("\\s+"), " ").also {
    require(it.isNotBlank()) { "Memory content is required." }
    require(it.length <= LocalDataRepository.MAX_MEMORY_CHARS) { "Memory content is too long." }
}

internal fun rankMemories(memories: List<MemoryRecord>, query: String): List<MemoryRecord> {
    val terms = normalizeMemory(query).split(' ').filter { it.length >= 2 }.distinct()
    if (terms.isEmpty()) return emptyList()
    return memories.mapNotNull { memory ->
        val score = terms.count(memory.normalizedContent::contains)
        if (score == 0) null else memory to score
    }.sortedWith(compareByDescending<Pair<MemoryRecord, Int>> { it.second }.thenByDescending { it.first.updatedAt })
        .map(Pair<MemoryRecord, Int>::first)
}
