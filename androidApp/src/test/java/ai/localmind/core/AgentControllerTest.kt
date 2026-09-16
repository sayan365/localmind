package ai.localmind.core

import ai.localmind.core.tools.createMockGoogleTools
import ai.localmind.core.tools.extractDeadlines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentControllerTest {
    private val primaryRequest = "Find the admission information Rahul sent me, summarize the important dates, and create reminders for them."

    @Test
    fun primaryWorkflowPausesForConfirmationBeforeCalendarCreation() {
        val runtime = runtime()

        val result = runtime.controller.start(primaryRequest)

        assertEquals(AgentState.WAITING_FOR_CONFIRMATION, result.state)
        assertTrue(result.needsConfirmation)
        assertEquals(3, result.proposedEvents.size)
        assertEquals(0, runtime.store.calendarEvents.size)
    }

    @Test
    fun primaryWorkflowCreatesAndVerifiesAfterConfirmation() {
        val runtime = runtime()

        runtime.controller.start(primaryRequest)
        val result = runtime.controller.approvePending()

        assertEquals(AgentState.COMPLETED, result.state)
        assertEquals(3, result.createdEvents.size)
        assertTrue(runtime.trace.list().any { it.type == "verification_completed" && it.payload["verified"] == true })
    }

    @Test
    fun cancellationCreatesNoCalendarEvents() {
        val runtime = runtime()

        runtime.controller.start(primaryRequest)
        val result = runtime.controller.cancel()

        assertEquals(AgentState.CANCELLED, result.state)
        assertEquals(0, runtime.store.calendarEvents.size)
    }

    @Test
    fun ambiguousRequestFailsHonestly() {
        val runtime = runtime()

        val result = runtime.controller.start("Please help me.")

        assertEquals(AgentState.FAILED, result.state)
        assertTrue(result.summary.contains("more specific"))
    }

    @Test
    fun dateExtractionIsStableAcrossTimezones() {
        val deadlines = extractDeadlines(
            """
            Application deadline: September 20, 2026.
            Document verification: September 25, 2026.
            Admission confirmation: September 30, 2026.
            """.trimIndent()
        )

        assertEquals(listOf("2026-09-20", "2026-09-25", "2026-09-30"), deadlines.map { it.date })
    }

    private fun runtime(): RuntimeFixture {
        val trace = EventTrace()
        val store = DemoStore()
        val registry = ToolRegistry(trace)
        createMockGoogleTools(store).forEach(registry::register)
        return RuntimeFixture(
            controller = AgentController(MockModelEngine(), registry, trace),
            trace = trace,
            store = store
        )
    }

    private data class RuntimeFixture(
        val controller: AgentController,
        val trace: EventTrace,
        val store: DemoStore
    )
}
