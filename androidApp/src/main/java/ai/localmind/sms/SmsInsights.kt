package ai.localmind.sms

import android.content.ContentResolver
import android.provider.Telephony
import java.text.NumberFormat
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class SmsRecord(
    val id: Long,
    val sender: String,
    val body: String,
    val receivedAtMillis: Long
)

sealed interface SmsQuery {
    data object UnreadCount : SmsQuery
    data class LatestOtp(val serviceHint: String?) : SmsQuery
    data class LatestTransaction(val accountSuffix: String?) : SmsQuery
    data class LatestBalance(val accountSuffix: String?) : SmsQuery
    data class Spending(val period: YearMonth, val accountSuffix: String?) : SmsQuery
    data object AccountSummary : SmsQuery
}

object SmsQueryParser {
    fun parse(input: String, clock: Clock = Clock.systemDefaultZone()): SmsQuery? {
        val text = input.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), " ").trim()
        val suffix = ACCOUNT_SUFFIX.find(text)?.groupValues?.get(1)

        if (isUnreadCountQuestion(text)) return SmsQuery.UnreadCount

        if (OTP_WORDS.any { text.containsWord(it) } &&
            (REQUEST_WORDS.any { text.containsWord(it) } || PERSONAL_WORDS.any { text.containsWord(it) })
        ) {
            return SmsQuery.LatestOtp(extractServiceHint(text))
        }
        if (text.contains("how many") && (text.containsWord("account") || text.containsWord("accounts"))) return SmsQuery.AccountSummary

        val personal = PERSONAL_WORDS.any { text.containsWord(it) }
        if (!personal) return null

        if (SPENDING_WORDS.any { text.containsWord(it) } && PERIOD_WORDS.any { text.containsWord(it) }) {
            return SmsQuery.Spending(parsePeriod(text, clock), suffix)
        }
        if (BALANCE_WORDS.any { text.containsWord(it) }) return SmsQuery.LatestBalance(suffix)
        if (TRANSACTION_WORDS.any { text.containsWord(it) } && RECENCY_WORDS.any { text.containsWord(it) }) {
            return SmsQuery.LatestTransaction(suffix)
        }
        return null
    }

    private fun isUnreadCountQuestion(text: String): Boolean {
        val mentionsMessages = MESSAGE_WORDS.any { text.containsWord(it) }
        val mentionsUnread = UNREAD_WORDS.any { text.containsWord(it) } || text.contains("not read")
        val asksForCount = COUNT_WORDS.any { text.contains(it) }
        return mentionsMessages && mentionsUnread && asksForCount
    }

    private fun parsePeriod(text: String, clock: Clock): YearMonth {
        val current = YearMonth.now(clock)
        if (text.contains("last month") || text.contains("previous month")) return current.minusMonths(1)
        MONTHS.entries.firstOrNull { text.containsWord(it.key) }?.let { (name, month) ->
            val year = Regex("\\b20\\d{2}\\b").find(text)?.value?.toIntOrNull() ?: current.year
            return YearMonth.of(year, month)
        }
        return current
    }

    private fun extractServiceHint(text: String): String? {
        val match = Regex("\\b(?:for|from|on)\\s+([a-z0-9][a-z0-9 ._-]{1,30})$").find(text) ?: return null
        return match.groupValues[1]
            .replace(Regex("\\b(?:please|account|app|service|message|sms)$"), "")
            .trim()
            .takeIf { it.length >= 2 }
    }

    private fun String.containsWord(word: String): Boolean = split(' ').contains(word)

    private val OTP_WORDS = setOf("otp", "code", "passcode", "verification")
    private val MESSAGE_WORDS = setOf("message", "messages", "sms", "text", "texts")
    private val UNREAD_WORDS = setOf("unread", "new", "unseen")
    private val COUNT_WORDS = setOf("how many", "count", "number", "do i have", "are there")
    private val REQUEST_WORDS = setOf("what", "show", "find", "get", "tell", "latest", "last", "recent")
    private val PERSONAL_WORDS = setOf("my", "mine", "i", "account", "bank")
    private val SPENDING_WORDS = setOf("spend", "spent", "spending", "expense", "expenses", "debited", "outgoing")
    private val PERIOD_WORDS = setOf("month", "monthly", "january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november", "december")
    private val BALANCE_WORDS = setOf("balance", "funds", "left")
    private val TRANSACTION_WORDS = setOf("transaction", "payment", "purchase", "debit", "credit", "paid", "spent")
    private val RECENCY_WORDS = setOf("last", "latest", "recent", "newest")
    private val ACCOUNT_SUFFIX = Regex("\\b(?:account|acct|a c)\\s*(?:ending|number|no)?\\s*(?:x+)?(\\d{3,4})\\b")
    private val MONTHS = linkedMapOf(
        "january" to 1, "february" to 2, "march" to 3, "april" to 4,
        "may" to 5, "june" to 6, "july" to 7, "august" to 8,
        "september" to 9, "october" to 10, "november" to 11, "december" to 12
    )
}

class SmsRepository(private val resolver: ContentResolver) {
    fun countUnread(): Int = resolver.query(
        Telephony.Sms.Inbox.CONTENT_URI,
        arrayOf(Telephony.Sms._ID),
        "${Telephony.Sms.READ} = 0",
        null,
        null
    )?.use { it.count } ?: 0

    fun readSince(sinceMillis: Long, limit: Int = 1_000): List<SmsRecord> {
        val records = ArrayList<SmsRecord>(minOf(limit, 128))
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE
        )
        resolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            projection,
            "${Telephony.Sms.DATE} >= ?",
            arrayOf(sinceMillis.toString()),
            "${Telephony.Sms.DATE} DESC"
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
            val senderIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (cursor.moveToNext() && records.size < limit) {
                val body = cursor.getString(bodyIndex)?.trim().orEmpty()
                if (body.isNotEmpty()) {
                    records += SmsRecord(
                        id = cursor.getLong(idIndex),
                        sender = cursor.getString(senderIndex).orEmpty().ifBlank { "Unknown sender" },
                        body = body.take(MAX_BODY_CHARS),
                        receivedAtMillis = cursor.getLong(dateIndex)
                    )
                }
            }
        }
        return records
    }

    private companion object {
        const val MAX_BODY_CHARS = 2_000
    }
}

sealed interface SmsInsightResult {
    data class Text(val message: String) : SmsInsightResult
    data class Otp(val code: String, val sender: String, val receivedAtMillis: Long) : SmsInsightResult
}

object SmsInsightAnalyzer {
    fun analyze(query: SmsQuery, messages: List<SmsRecord>, clock: Clock = Clock.systemDefaultZone()): SmsInsightResult {
        return when (query) {
            SmsQuery.UnreadCount -> SmsInsightResult.Text("Unread SMS messages: ${messages.size}")
            is SmsQuery.LatestOtp -> latestOtp(query, messages, clock)
            is SmsQuery.LatestTransaction -> latestTransaction(query, messages, clock)
            is SmsQuery.LatestBalance -> latestBalance(query, messages, clock)
            is SmsQuery.Spending -> spending(query, messages, clock)
            SmsQuery.AccountSummary -> accountSummary(messages)
        }
    }

    private fun latestOtp(query: SmsQuery.LatestOtp, messages: List<SmsRecord>, clock: Clock): SmsInsightResult {
        val cutoff = clock.instant().minusSeconds(OTP_MAX_AGE_SECONDS).toEpochMilli()
        val hint = query.serviceHint?.lowercase(Locale.US)
        val match = messages.asSequence()
            .filter { it.receivedAtMillis >= cutoff }
            .filter { hint == null || it.sender.lowercase(Locale.US).contains(hint) || it.body.lowercase(Locale.US).contains(hint) }
            .filter { OTP_CONTEXT.containsMatchIn(it.body) }
            .mapNotNull { message -> otpCode(message.body)?.let { Triple(message, it, message.receivedAtMillis) } }
            .maxByOrNull { it.third }
        return if (match == null) {
            SmsInsightResult.Text("I couldn't find a recent${hint?.let { " $it" }.orEmpty()} OTP in messages from the last 24 hours.")
        } else SmsInsightResult.Otp(match.second, match.first.sender, match.first.receivedAtMillis)
    }

    private fun latestTransaction(query: SmsQuery.LatestTransaction, messages: List<SmsRecord>, clock: Clock): SmsInsightResult {
        val transaction = messages.asSequence().mapNotNull(::parseTransaction)
            .filter { query.accountSuffix == null || it.accountSuffix == query.accountSuffix }
            .maxByOrNull { it.message.receivedAtMillis }
            ?: return SmsInsightResult.Text("I couldn't find a recent bank transaction message${accountText(query.accountSuffix)}.")
        val direction = if (transaction.debit) "spent or debited" else "credited"
        return SmsInsightResult.Text(
            "**Latest transaction found:** ${formatMoney(transaction.amount)} $direction${transaction.merchant?.let { " at $it" }.orEmpty()}.\n\n" +
                "Account: ${transaction.accountSuffix?.let { "ending $it" } ?: "not identified"}\n" +
                "Source: ${transaction.message.sender}, ${formatTime(transaction.message.receivedAtMillis, clock.zone)}"
        )
    }

    private fun latestBalance(query: SmsQuery.LatestBalance, messages: List<SmsRecord>, clock: Clock): SmsInsightResult {
        val balance = messages.asSequence().mapNotNull(::parseBalance)
            .filter { query.accountSuffix == null || it.accountSuffix == query.accountSuffix }
            .maxByOrNull { it.message.receivedAtMillis }
            ?: return SmsInsightResult.Text("I couldn't find a balance in recent bank messages${accountText(query.accountSuffix)}.")
        return SmsInsightResult.Text(
            "**Latest balance mentioned in SMS:** ${formatMoney(balance.amount)}\n\n" +
                "Account: ${balance.accountSuffix?.let { "ending $it" } ?: "not identified"}\n" +
                "Source: ${balance.message.sender}, ${formatTime(balance.message.receivedAtMillis, clock.zone)}\n\n" +
                "This may not be your live bank balance."
        )
    }

    private fun spending(query: SmsQuery.Spending, messages: List<SmsRecord>, clock: Clock): SmsInsightResult {
        val zone = clock.zone
        val transactions = messages.asSequence().mapNotNull(::parseTransaction)
            .filter { it.debit }
            .filter { YearMonth.from(Instant.ofEpochMilli(it.message.receivedAtMillis).atZone(zone)) == query.period }
            .filter { query.accountSuffix == null || it.accountSuffix == query.accountSuffix }
            .distinctBy { it.message.id }
            .toList()
        if (transactions.isEmpty()) {
            return SmsInsightResult.Text("I couldn't find debit transaction messages for ${query.period.format(MONTH_FORMAT)}${accountText(query.accountSuffix)}.")
        }
        val total = transactions.sumOf { it.amount }
        return SmsInsightResult.Text(
            "**Spending found for ${query.period.format(MONTH_FORMAT)}:** ${formatMoney(total)}\n\n" +
                "Based on ${transactions.size} debit message${if (transactions.size == 1) "" else "s"}${accountText(query.accountSuffix)}. " +
                "Cash, missing messages, reversals, and differently formatted alerts may not be included."
        )
    }

    private fun accountSummary(messages: List<SmsRecord>): SmsInsightResult {
        val accounts = messages.mapNotNull(::accountSuffix).distinct().sorted()
        return if (accounts.isEmpty()) SmsInsightResult.Text("I couldn't identify account endings in recent bank messages.")
        else SmsInsightResult.Text("I found ${accounts.size} account ending${if (accounts.size == 1) "" else "s"} in recent messages:\n\n" + accounts.joinToString("\n") { "- $it" })
    }

    private fun parseTransaction(message: SmsRecord): Transaction? {
        val body = message.body
        val debit = DEBIT_WORDS.containsMatchIn(body)
        val credit = CREDIT_WORDS.containsMatchIn(body)
        if (!debit && !credit) return null
        val amount = TRANSACTION_AMOUNT_PATTERNS.firstNotNullOfOrNull { pattern ->
            pattern.find(body)?.groupValues?.get(1)?.toAmount()
        } ?: return null
        val merchant = MERCHANT.find(body)?.groupValues?.get(1)?.trim()?.trimEnd('.', ',')?.take(40)
        return Transaction(message, amount, debit, accountSuffix(message), merchant)
    }

    private fun parseBalance(message: SmsRecord): Balance? {
        val amount = BALANCE_AMOUNT.find(message.body)?.groupValues?.get(1)?.toAmount() ?: return null
        return Balance(message, amount, accountSuffix(message))
    }

    private fun otpCode(body: String): String? = OTP_AFTER.find(body)?.groupValues?.get(1)
        ?: OTP_BEFORE.find(body)?.groupValues?.get(1)
        ?: OTP_CODE.find(body)?.groupValues?.get(1)

    private fun accountSuffix(message: SmsRecord): String? = ACCOUNT_IN_MESSAGE.find(message.body)?.groupValues?.get(1)

    private fun String.toAmount(): Double? = replace(",", "").toDoubleOrNull()?.takeIf { it >= 0.0 }
    private fun accountText(suffix: String?): String = suffix?.let { " for account ending $it" }.orEmpty()
    private fun formatMoney(amount: Double): String = "INR " + NumberFormat.getNumberInstance(Locale("en", "IN")).apply { maximumFractionDigits = 2 }.format(amount)
    private fun formatTime(millis: Long, zone: ZoneId): String = Instant.ofEpochMilli(millis).atZone(zone).format(TIME_FORMAT)

    private data class Transaction(val message: SmsRecord, val amount: Double, val debit: Boolean, val accountSuffix: String?, val merchant: String?)
    private data class Balance(val message: SmsRecord, val amount: Double, val accountSuffix: String?)

    private val OTP_CONTEXT = Regex("(?i)\\b(?:otp|one[ -]?time password|verification code|security code|passcode)\\b")
    private val OTP_AFTER = Regex("(?i)\\b(?:otp|one[ -]?time password|verification code|security code|passcode)\\b(?:\\s+(?:is|for))?\\s*[:=-]?\\s*(\\d{4,8})(?!\\d)")
    private val OTP_BEFORE = Regex("(?i)(?<!\\d)(\\d{4,8})\\s+(?:is\\s+)?(?:your\\s+)?(?:otp|one[ -]?time password|verification code|security code|passcode)\\b")
    private val OTP_CODE = Regex("(?<!\\d)(\\d{4,8})(?!\\d)")
    private val DEBIT_WORDS = Regex("(?i)\\b(?:debited|debit|spent|paid|purchase|withdrawn|withdrawal|sent)\\b")
    private val CREDIT_WORDS = Regex("(?i)\\b(?:credited|credit|received|deposited|refund)\\b")
    private val MONEY = "(?:INR|Rs\\.?|₹)\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)"
    private val TRANSACTION_AMOUNT_PATTERNS = listOf(
        Regex("(?i)$MONEY.{0,35}?\\b(?:debited|spent|paid|purchase(?:d)?|withdrawn|credited|received|deposited|refund(?:ed)?)\\b"),
        Regex("(?i)\\b(?:debited|spent|paid|purchase(?:d)?|withdrawn|credited|received|deposited|refund(?:ed)?)\\b(?:\\s+(?:by|of|for|with))?\\s*[:=-]?\\s*$MONEY")
    )
    private val BALANCE_AMOUNT = Regex("(?i)\\b(?:available |avail |current |closing )?(?:balance|bal)\\b[^0-9₹]{0,20}$MONEY")
    private val ACCOUNT_IN_MESSAGE = Regex("(?i)\\b(?:a/?c|acct|account)[^0-9]{0,16}(?:x+|\\*+)?(\\d{3,4})\\b")
    private val MERCHANT = Regex("(?i)\\b(?:at|to)\\s+([A-Z0-9][A-Z0-9 &._-]{1,40}?)(?:\\s+(?:on|ref|avl|available|balance)|[.,]|$)")
    private val TIME_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a")
    private val MONTH_FORMAT = DateTimeFormatter.ofPattern("MMMM yyyy")
    private const val OTP_MAX_AGE_SECONDS = 24L * 60L * 60L
}
