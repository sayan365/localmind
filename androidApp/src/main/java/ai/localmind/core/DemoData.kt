package ai.localmind.core

data class DemoEmail(
    val id: String,
    val from: String,
    val subject: String,
    val body: String,
    val driveDocumentId: String,
    val receivedAt: String
)

data class DemoDocument(
    val id: String,
    val title: String,
    val body: String
)

data class Deadline(
    val title: String,
    val date: String,
    val reason: String
)

data class CalendarEvent(
    val id: String,
    val title: String,
    val date: String,
    val description: String
)

class DemoStore {
    val emails = mutableListOf(
        DemoEmail(
            id = "email_rahul_admission_2026",
            from = "Rahul Sharma <rahul.demo@example.test>",
            subject = "University admission information for 2026",
            body = "Hey, I uploaded the admission information document. Please check the deadlines and set reminders.",
            driveDocumentId = "drive_admission_info_2026",
            receivedAt = "2026-09-10T09:30:00Z"
        )
    )

    val documents = mutableListOf(
        DemoDocument(
            id = "drive_admission_info_2026",
            title = "University Admission Information 2026",
            body = """
                Application deadline: September 20, 2026.
                Document verification: September 25, 2026.
                Admission confirmation: September 30, 2026.
            """.trimIndent()
        )
    )

    val calendarEvents = mutableListOf<CalendarEvent>()
}
