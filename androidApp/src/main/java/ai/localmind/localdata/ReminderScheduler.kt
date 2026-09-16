package ai.localmind.localdata

import ai.localmind.MainActivity
import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import java.util.concurrent.Executors

object ReminderScheduler {
    fun schedule(context: Context, reminder: ReminderRecord) {
        require(reminder.triggerAt > System.currentTimeMillis()) { "Reminder time must be in the future." }
        val manager = context.getSystemService(AlarmManager::class.java)
        manager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            reminder.triggerAt,
            checkNotNull(pendingIntent(context, reminder, PendingIntent.FLAG_UPDATE_CURRENT))
        )
    }

    fun cancel(context: Context, reminder: ReminderRecord) {
        val pending = pendingIntent(context, reminder, PendingIntent.FLAG_NO_CREATE) ?: return
        context.getSystemService(AlarmManager::class.java).cancel(pending)
        pending.cancel()
    }

    private fun pendingIntent(context: Context, reminder: ReminderRecord, lookupFlag: Int): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            reminder.id.toRequestCode(),
            Intent(context, ReminderReceiver::class.java).apply {
                putExtra(EXTRA_REMINDER_ID, reminder.id)
                putExtra(EXTRA_REMINDER_TITLE, reminder.title)
            },
            lookupFlag or PendingIntent.FLAG_IMMUTABLE
        )

    private fun Long.toRequestCode(): Int = (this xor (this ushr 32)).toInt()

    const val EXTRA_REMINDER_ID = "reminder_id"
    const val EXTRA_REMINDER_TITLE = "reminder_title"
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(ReminderScheduler.EXTRA_REMINDER_ID, -1L)
        val title = intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER_TITLE)?.takeIf(String::isNotBlank) ?: return
        if (id < 0L) return

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Local reminders", NotificationManager.IMPORTANCE_HIGH)
        )
        val canNotify = Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (canNotify) {
            val openApp = PendingIntent.getActivity(
                context,
                id.toInt(),
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            manager.notify(
                id.toInt(),
                Notification.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_popup_reminder)
                    .setContentTitle("LocalMind reminder")
                    .setContentText(title)
                    .setStyle(Notification.BigTextStyle().bigText(title))
                    .setContentIntent(openApp)
                    .setAutoCancel(true)
                    .build()
            )
        }

        val pending = goAsync()
        EXECUTOR.execute {
            runCatching { LocalDataRepository(context).markReminderDelivered(id) }
            pending?.finish()
        }
    }

    companion object {
        private const val CHANNEL_ID = "local_reminders"
        private val EXECUTOR = Executors.newSingleThreadExecutor()
    }
}

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        EXECUTOR.execute {
            runCatching {
                val repository = LocalDataRepository(context)
                repository.expirePastReminders()
                repository.scheduledReminders()
                    .filter { it.triggerAt > System.currentTimeMillis() }
                    .forEach { ReminderScheduler.schedule(context, it) }
            }
            pending?.finish()
        }
    }

    companion object {
        private val EXECUTOR = Executors.newSingleThreadExecutor()
    }
}
