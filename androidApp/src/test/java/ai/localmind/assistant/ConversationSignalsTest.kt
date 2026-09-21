package ai.localmind.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConversationSignalsTest {
    @Test
    fun handlesShortAcknowledgementsWithoutUsingTheModel() {
        assertEquals("Got it.", ConversationSignals.localResponse("ok"))
        assertEquals("Got it.", ConversationSignals.localResponse("Okay!"))
        assertEquals("Got it.", ConversationSignals.localResponse("I understand."))
        assertEquals("You're welcome.", ConversationSignals.localResponse("Thank you"))
        assertEquals("Glad that helped.", ConversationSignals.localResponse("Perfect"))
    }

    @Test
    fun doesNotConsumeRequestsThatNeedRoutingOrReasoning() {
        assertNull(ConversationSignals.localResponse("Okay, turn off the flashlight"))
        assertNull(ConversationSignals.localResponse("Good explanation of photosynthesis"))
        assertNull(ConversationSignals.localResponse("Yes, send the message"))
    }
}
