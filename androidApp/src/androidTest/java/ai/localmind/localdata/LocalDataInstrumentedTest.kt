package ai.localmind.localdata

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class LocalDataInstrumentedTest {
    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var repository: LocalDataRepository

    @Before
    fun setUp() {
        repository = LocalDataRepository(context)
        repository.clearMemories()
        repository.clearReminders()
    }

    @Test
    fun memoryCrudPersistsInRoom() {
        val saved = repository.saveMemory("My passport expires in 2028", now = 100L)

        assertTrue(saved.id > 0)
        assertEquals(saved.id, repository.searchMemories("passport").single().id)

        val updated = repository.updateMemory(saved.id, "My passport expires in 2029", now = 200L)
        assertEquals("My passport expires in 2029", repository.listMemories().single().content)
        assertEquals(200L, updated.updatedAt)

        assertTrue(repository.deleteMemory(saved.id))
        assertTrue(repository.listMemories().isEmpty())
    }

    @Test
    fun reminderReceiverMarksStoredReminderDelivered() {
        val reminder = repository.addReminder(
            title = "Pay the electricity bill",
            triggerAt = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(10)
        )
        ReminderScheduler.schedule(context, reminder)

        context.sendBroadcast(
            Intent(context, ReminderReceiver::class.java)
                .putExtra(ReminderScheduler.EXTRA_REMINDER_ID, reminder.id)
                .putExtra(ReminderScheduler.EXTRA_REMINDER_TITLE, reminder.title)
        )

        val deadline = System.currentTimeMillis() + 5_000L
        while (repository.scheduledReminders().isNotEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50L)
        }
        assertTrue(repository.scheduledReminders().isEmpty())
        assertEquals(ReminderRecord.STATUS_DELIVERED, repository.findReminder(reminder.id)?.status)
        ReminderScheduler.cancel(context, reminder)
    }

    @Test
    fun overdueReminderDoesNotRemainPendingAfterRestore() {
        val now = System.currentTimeMillis()
        val reminder = repository.addReminder("Past reminder", now + 1_000L, now = now)

        repository.expirePastReminders(now + 2_000L)

        assertTrue(repository.scheduledReminders().isEmpty())
        assertEquals(ReminderRecord.STATUS_EXPIRED, repository.findReminder(reminder.id)?.status)
    }
}
