package ai.localmind.device.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponseQualityGateTest {
    @Test
    fun rejectsTemplateLeaksAndFalseActionClaims() {
        assertEquals("model template artifact", ResponseQualityGate.rejectionReason("Hello", "<|im_start|>system", null))
        assertEquals("unverified action claim", ResponseQualityGate.rejectionReason("Draft it", "I have sent the message.", null))
    }

    @Test
    fun rejectsAnUnrelatedRepeatedAnswerButAllowsRequestedRewrite() {
        val previous = "The derivative of sin(x) is cos(x)."
        assertEquals("repeated previous answer", ResponseQualityGate.rejectionReason("Open WhatsApp", previous, previous))
        assertNull(ResponseQualityGate.rejectionReason("Rewrite that more formally", previous, previous))
    }

    @Test
    fun acceptsAUsefulNormalResponse() {
        assertNull(ResponseQualityGate.rejectionReason("Write a leave message", "I am requesting leave today due to a personal matter.", null))
        assertTrue(ResponseQualityGate.retryPrompt("What is photosynthesis?").contains("current request"))
    }
}
