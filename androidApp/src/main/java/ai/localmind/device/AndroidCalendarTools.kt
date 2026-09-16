package ai.localmind.device

import ai.localmind.core.CalendarEvent
import ai.localmind.core.InputSchema
import ai.localmind.core.RiskLevel
import ai.localmind.core.Tool
import ai.localmind.core.ToolResult
import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import java.time.LocalDate
import java.time.ZoneOffset

fun createAndroidCalendarTools(context: Context): List<Tool> {
    val connector = AndroidCalendarConnector(context.applicationContext)
    return listOf(
        object : Tool {
            override val id = "calendar.create_event"
            override val name = "Create device Calendar events"
            override val description = "Creates all-day events in a writable Android calendar."
            override val riskLevel = RiskLevel.CONFIRM
            override val inputSchema = InputSchema(setOf("events"), mapOf("events" to List::class.java))

            override fun execute(arguments: Map<String, Any?>): ToolResult {
                @Suppress("UNCHECKED_CAST")
                return connector.create(arguments["events"] as List<CalendarEvent>)
            }
        },
        object : Tool {
            override val id = "calendar.verify_event"
            override val name = "Verify device Calendar events"
            override val description = "Reads inserted Android Calendar event IDs to verify creation."
            override val riskLevel = RiskLevel.SAFE
            override val inputSchema = InputSchema(setOf("eventIds"), mapOf("eventIds" to List::class.java))

            override fun execute(arguments: Map<String, Any?>): ToolResult {
                @Suppress("UNCHECKED_CAST")
                return connector.verify(arguments["eventIds"] as List<String>)
            }
        }
    )
}

class AndroidCalendarConnector(private val context: Context) {
    fun hasPermissions(): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
            context.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun create(events: List<CalendarEvent>): ToolResult {
        if (!hasPermissions()) return failure("calendar_permission_required", "Calendar permission is required. Nothing was created.")
        val calendarId = writableCalendarId()
            ?: return failure("no_writable_calendar", "No writable calendar is available on this device. Add a calendar account and try again.")

        val created = mutableListOf<CalendarEvent>()
        for (event in events) {
            val date = runCatching { LocalDate.parse(event.date) }.getOrNull()
                ?: return failure("invalid_date", "The event date could not be understood. Nothing else was created.")
            val start = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.TITLE, event.title)
                put(CalendarContract.Events.DESCRIPTION, event.description)
                put(CalendarContract.Events.DTSTART, start)
                put(CalendarContract.Events.DTEND, start + DAY_MILLIS)
                put(CalendarContract.Events.ALL_DAY, 1)
                put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
            }
            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
                ?: return failure("calendar_insert_failed", "Calendar stopped accepting events after ${created.size} were created.")
            created += event.copy(id = ContentUris.parseId(uri).toString())
        }
        return ToolResult(true, mapOf("events" to created))
    }

    fun verify(eventIds: List<String>): ToolResult {
        if (!hasPermissions()) return failure("calendar_permission_required", "Calendar permission is required to verify events.")
        val numericIds = eventIds.mapNotNull(String::toLongOrNull)
        if (numericIds.size != eventIds.size) return ToolResult(true, mapOf("verified" to false, "checked" to 0, "missing" to eventIds))
        val found = mutableSetOf<String>()
        val selection = "${CalendarContract.Events._ID} IN (${numericIds.joinToString { "?" }})"
        context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(CalendarContract.Events._ID),
            selection,
            numericIds.map(Long::toString).toTypedArray(),
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) found += cursor.getLong(0).toString()
        }
        val missing = eventIds.filterNot(found::contains)
        return ToolResult(true, mapOf("verified" to missing.isEmpty(), "checked" to found.size, "missing" to missing))
    }

    private fun writableCalendarId(): Long? {
        val projection = arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL)
        context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getInt(1) >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) return cursor.getLong(0)
            }
        }
        return null
    }

    private fun failure(code: String, message: String) = ToolResult(false, errorCode = code, errorMessage = message)

    private companion object {
        const val DAY_MILLIS = 86_400_000L
    }
}
