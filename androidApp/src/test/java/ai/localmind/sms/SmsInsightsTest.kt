package ai.localmind.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset

class SmsInsightsTest {
    private val clock = Clock.fixed(Instant.parse("2026-09-21T08:00:00Z"), ZoneOffset.UTC)

    @Test
    fun understandsVariedPersonalSmsQuestions() {
        assertEquals(SmsQuery.UnreadCount, SmsQueryParser.parse("How many unread messages do I have right now?", clock))
        assertEquals(SmsQuery.UnreadCount, SmsQueryParser.parse("Do I have any new SMS?", clock))
        assertEquals(SmsQuery.UnreadCount, SmsQueryParser.parse("Count my unseen texts", clock))
        assertEquals(SmsQuery.LatestOtp("netflix"), SmsQueryParser.parse("Okay, what is my latest OTP for Netflix?", clock))
        assertEquals(SmsQuery.LatestOtp(null), SmsQueryParser.parse("Show my OTP", clock))
        assertEquals(SmsQuery.LatestTransaction(null), SmsQueryParser.parse("Which was my most recent transaction?", clock))
        assertEquals(SmsQuery.LatestBalance(null), SmsQueryParser.parse("How much balance is left in my bank account?", clock))
        assertEquals(SmsQuery.Spending(YearMonth.of(2026, 9), null), SmsQueryParser.parse("How much did I spend this month?", clock))
        assertEquals(SmsQuery.Spending(YearMonth.of(2026, 8), null), SmsQueryParser.parse("Show my expenses for last month", clock))
        assertEquals(SmsQuery.AccountSummary, SmsQueryParser.parse("How many bank accounts do I have?", clock))
    }

    @Test
    fun doesNotTreatGeneralFinanceQuestionsAsPrivateSmsQueries() {
        assertEquals(null, SmsQueryParser.parse("What is a bank transaction?", clock))
        assertEquals(null, SmsQueryParser.parse("Explain monthly expenses", clock))
    }

    @Test
    fun findsLatestOtpForRequestedService() {
        val result = SmsInsightAnalyzer.analyze(
            SmsQuery.LatestOtp("netflix"),
            listOf(
                sms(1, "VM-NETFLX", "Netflix verification code is 482913. Do not share it.", "2026-09-21T07:58:00Z"),
                sms(2, "VK-BANK", "OTP 111222 for your transaction", "2026-09-21T07:59:00Z")
            ),
            clock
        )

        assertEquals(SmsInsightResult.Otp("482913", "VM-NETFLX", Instant.parse("2026-09-21T07:58:00Z").toEpochMilli()), result)
    }

    @Test
    fun reportsLatestTransactionAndBalanceWithEvidence() {
        val messages = listOf(
            sms(1, "AD-HDFCBK", "Rs.500.00 debited from A/c XX1234 at METRO on 21-09. Avl Bal Rs.12,345.67", "2026-09-21T07:30:00Z"),
            sms(2, "AD-HDFCBK", "INR 200.00 debited from account XX1234 at CAFE. Available balance INR 12,845.67", "2026-09-20T07:30:00Z")
        )

        val transaction = SmsInsightAnalyzer.analyze(SmsQuery.LatestTransaction("1234"), messages, clock) as SmsInsightResult.Text
        val balance = SmsInsightAnalyzer.analyze(SmsQuery.LatestBalance("1234"), messages, clock) as SmsInsightResult.Text

        assertTrue(transaction.message.contains("INR 500"))
        assertTrue(transaction.message.contains("Source: AD-HDFCBK"))
        assertTrue(balance.message.contains("INR 12,345.67"))
        assertTrue(balance.message.contains("may not be your live bank balance"))
    }

    @Test
    fun totalsOnlyDebitMessagesInRequestedMonth() {
        val messages = listOf(
            sms(1, "BANK", "Rs.500.00 debited from A/c XX1234 at METRO. Avl Bal Rs.12,345.67", "2026-09-02T07:00:00Z"),
            sms(2, "BANK", "Rs.250.50 paid from account XX1234", "2026-09-10T07:00:00Z"),
            sms(3, "BANK", "INR 1000 credited to A/c XX1234", "2026-09-11T07:00:00Z"),
            sms(4, "BANK", "INR 99 debited from A/c XX1234", "2026-08-30T07:00:00Z")
        )

        val result = SmsInsightAnalyzer.analyze(SmsQuery.Spending(YearMonth.of(2026, 9), "1234"), messages, clock) as SmsInsightResult.Text

        assertTrue(result.message.contains("INR 750.5"))
        assertTrue(result.message.contains("2 debit messages"))
    }

    private fun sms(id: Long, sender: String, body: String, instant: String) =
        SmsRecord(id, sender, body, Instant.parse(instant).toEpochMilli())
}
