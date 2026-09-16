package ai.localmind.device

import java.time.Clock
import java.time.LocalDate

sealed interface BasicDeviceAction {
    data class OpenApp(val app: SupportedApp) : BasicDeviceAction
    data class SetTorch(val enabled: Boolean) : BasicDeviceAction
    data class Media(val command: MediaCommand) : BasicDeviceAction
    data class Volume(val command: VolumeCommand) : BasicDeviceAction
    data class SendWhatsApp(val message: String, val recipientHint: String? = null) : BasicDeviceAction
    data class ComposeEmail(val address: String, val message: String) : BasicDeviceAction
    data object PickDriveDocument : BasicDeviceAction
    data class SearchLocalInbox(val query: String) : BasicDeviceAction
    data class CreateCalendarEvent(val title: String, val date: LocalDate) : BasicDeviceAction
}

enum class SupportedApp(val displayName: String, val packageNames: List<String>) {
    WHATSAPP("WhatsApp", listOf("com.whatsapp")),
    GMAIL("Gmail", listOf("com.google.android.gm")),
    DRIVE("Google Drive", listOf("com.google.android.apps.docs")),
    CALENDAR("Calendar", listOf("com.google.android.calendar")),
    YOUTUBE_MUSIC("YouTube Music", listOf("com.google.android.apps.youtube.music")),
    YOUTUBE("YouTube", listOf("com.google.android.youtube")),
    SPOTIFY("Spotify", listOf("com.spotify.music"))
}

enum class MediaCommand { PLAY, PAUSE, STOP, NEXT, PREVIOUS }

enum class VolumeCommand { UP, DOWN, MUTE, UNMUTE }

object BasicDeviceActionParser {
    fun parse(input: String, previousAction: BasicDeviceAction? = null, clock: Clock = Clock.systemDefaultZone()): BasicDeviceAction? {
        parseWhatsApp(input)?.let { return it }
        parseEmail(input)?.let { return it }
        val text = input.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
        parseInboxSearch(text)?.let { return it }
        parseDrivePicker(text)?.let { return it }
        parseCalendar(text, clock)?.let { return it }
        parseApp(text)?.let { return it }
        parseTorch(text)?.let { return it }
        parseVolume(text)?.let { return it }
        parseMedia(text)?.let { return it }
        return parseContextualFollowUp(text, previousAction)
    }

    private fun parseEmail(input: String): BasicDeviceAction.ComposeEmail? {
        if (!Regex("(?i)\\b(?:send|write|compose|email)\\b").containsMatchIn(input)) return null
        val address = Regex("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", RegexOption.IGNORE_CASE).find(input)?.value ?: return null
        val message = Regex("(?i)\\b(?:saying|that)\\s+(.+)$").find(input)?.groupValues?.get(1)?.trim()
            ?: input.substringAfter(':', "").trim()
        return message.takeIf(String::isNotBlank)?.let { BasicDeviceAction.ComposeEmail(address, it) }
    }

    private fun parseDrivePicker(text: String): BasicDeviceAction.PickDriveDocument? {
        val mentionsDrive = text.containsWord("drive")
        val mentionsFile = listOf("file", "document", "pdf", "report").any { text.containsWord(it) }
        val requestsSelection = listOf("find", "choose", "select", "pick", "get", "import").any { text.containsWord(it) }
        return BasicDeviceAction.PickDriveDocument.takeIf { mentionsDrive && mentionsFile && requestsSelection }
    }

    private fun parseInboxSearch(text: String): BasicDeviceAction.SearchLocalInbox? {
        if (!text.containsWord("inbox") || listOf("find", "search").none { text.containsWord(it) }) return null
        val query = text.replace(Regex("\\b(?:find|search|for|in|my|local|shared|inbox)\\b"), " ")
            .replace(Regex("\\s+"), " ").trim()
        return query.takeIf(String::isNotBlank)?.let(BasicDeviceAction::SearchLocalInbox)
    }

    private fun parseWhatsApp(input: String): BasicDeviceAction.SendWhatsApp? {
        if (!input.contains("whatsapp", ignoreCase = true) || !Regex("(?i)\\b(send|tell)\\b").containsMatchIn(input)) return null
        val message = Regex("(?i)\\b(?:saying|that)\\s+(.+)$").find(input)?.groupValues?.get(1)?.trim()
            ?: input.substringAfter(':', "").trim()
        if (message.isBlank()) return null
        val recipient = Regex("(?i)\\b(?:to|tell)\\s+(.+?)(?:\\s+(?:on\\s+whatsapp|saying|that)|:)")
            .find(input)?.groupValues?.get(1)?.trim()?.takeIf(String::isNotBlank)
        return BasicDeviceAction.SendWhatsApp(message, recipient)
    }

    private fun parseCalendar(text: String, clock: Clock): BasicDeviceAction.CreateCalendarEvent? {
        val isAction = listOf("remind me", "add", "create", "schedule").any(text::contains)
        val mentionsCalendar = listOf("calendar", "event", "reminder", "remind me").any(text::contains) || text.containsWord("schedule")
        if (!isAction || !mentionsCalendar) return null
        val date = when {
            text.contains("tomorrow") -> LocalDate.now(clock).plusDays(1)
            text.contains("today") -> LocalDate.now(clock)
            else -> Regex("\\b20\\d{2} \\d{1,2} \\d{1,2}\\b").find(text)?.value?.split(' ')?.let {
                runCatching { LocalDate.of(it[0].toInt(), it[1].toInt(), it[2].toInt()) }.getOrNull()
            }
        } ?: return null
        val title = text
            .replace(Regex("\\b(?:add|create|schedule|a|an|the|calendar|event|reminder|remind|me|for|on|to|today|tomorrow)\\b"), " ")
            .replace(Regex("\\b20\\d{2} \\d{1,2} \\d{1,2}\\b"), " ")
            .replace(Regex("\\s+"), " ").trim()
            .ifBlank { "LocalMind reminder" }
        return BasicDeviceAction.CreateCalendarEvent(title.replaceFirstChar(Char::uppercase), date)
    }

    fun isAmbiguousStateCommand(input: String): Boolean {
        val text = input.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
        return text in setOf("turn on", "turn it on", "switch on", "switch it on", "turn off", "turn it off", "switch off", "switch it off")
    }

    private fun parseApp(text: String): BasicDeviceAction.OpenApp? {
        if (!OPEN_WORDS.any { text.containsWord(it) }) return null
        val app = when {
            text.contains("youtube music") -> SupportedApp.YOUTUBE_MUSIC
            text.containsWord("whatsapp") -> SupportedApp.WHATSAPP
            text.containsWord("gmail") || text.contains("google mail") -> SupportedApp.GMAIL
            text.containsWord("drive") -> SupportedApp.DRIVE
            text.containsWord("calendar") -> SupportedApp.CALENDAR
            text.containsWord("youtube") -> SupportedApp.YOUTUBE
            text.containsWord("spotify") -> SupportedApp.SPOTIFY
            else -> null
        }
        return app?.let(BasicDeviceAction::OpenApp)
    }

    private fun parseTorch(text: String): BasicDeviceAction.SetTorch? {
        if (TORCH_WORDS.none { text.containsWord(it) }) return null
        return when {
            OFF_WORDS.any { text.containsWord(it) } -> BasicDeviceAction.SetTorch(false)
            ON_WORDS.any { text.containsWord(it) } -> BasicDeviceAction.SetTorch(true)
            else -> null
        }
    }

    private fun parseMedia(text: String): BasicDeviceAction.Media? {
        val mentionsMedia = MEDIA_WORDS.any { text.containsWord(it) }
        return when {
            mentionsMedia && (text.containsWord("next") || text.containsWord("skip")) -> BasicDeviceAction.Media(MediaCommand.NEXT)
            mentionsMedia && (text.containsWord("previous") || text.containsWord("last")) -> BasicDeviceAction.Media(MediaCommand.PREVIOUS)
            mentionsMedia && (text.containsWord("pause") || text.contains("hold music")) -> BasicDeviceAction.Media(MediaCommand.PAUSE)
            mentionsMedia && text.containsWord("stop") -> BasicDeviceAction.Media(MediaCommand.STOP)
            mentionsMedia && (text.containsWord("resume") || text.containsWord("play")) -> BasicDeviceAction.Media(MediaCommand.PLAY)
            else -> null
        }
    }

    private fun parseVolume(text: String): BasicDeviceAction.Volume? {
        if (!text.containsWord("volume") && !text.containsWord("louder") && !text.containsWord("quieter")) return null
        return when {
            text.containsWord("unmute") -> BasicDeviceAction.Volume(VolumeCommand.UNMUTE)
            text.containsWord("mute") -> BasicDeviceAction.Volume(VolumeCommand.MUTE)
            text.containsWord("up") || text.containsWord("increase") || text.containsWord("louder") -> BasicDeviceAction.Volume(VolumeCommand.UP)
            text.containsWord("down") || text.containsWord("decrease") || text.containsWord("lower") || text.containsWord("quieter") -> BasicDeviceAction.Volume(VolumeCommand.DOWN)
            else -> null
        }
    }

    private fun parseContextualFollowUp(text: String, previousAction: BasicDeviceAction?): BasicDeviceAction? = when (previousAction) {
        is BasicDeviceAction.SetTorch -> when {
            text.isStateFollowUp(ON_WORDS) -> BasicDeviceAction.SetTorch(true)
            text.isStateFollowUp(OFF_WORDS) -> BasicDeviceAction.SetTorch(false)
            else -> null
        }
        is BasicDeviceAction.Media -> when {
            text in setOf("pause", "pause it") -> BasicDeviceAction.Media(MediaCommand.PAUSE)
            text in setOf("play", "play it", "resume", "resume it") -> BasicDeviceAction.Media(MediaCommand.PLAY)
            text in setOf("stop", "stop it", "turn it off", "turn off") -> BasicDeviceAction.Media(MediaCommand.STOP)
            text in setOf("next", "skip", "skip it") -> BasicDeviceAction.Media(MediaCommand.NEXT)
            text in setOf("previous", "go back") -> BasicDeviceAction.Media(MediaCommand.PREVIOUS)
            else -> null
        }
        is BasicDeviceAction.Volume -> when {
            text in setOf("turn it up", "increase it", "louder") -> BasicDeviceAction.Volume(VolumeCommand.UP)
            text in setOf("turn it down", "decrease it", "lower it", "quieter") -> BasicDeviceAction.Volume(VolumeCommand.DOWN)
            text in setOf("mute it") -> BasicDeviceAction.Volume(VolumeCommand.MUTE)
            text in setOf("unmute it") -> BasicDeviceAction.Volume(VolumeCommand.UNMUTE)
            else -> null
        }
        is BasicDeviceAction.OpenApp -> if (text in setOf("open it again", "launch it again")) previousAction else null
        is BasicDeviceAction.SendWhatsApp,
        is BasicDeviceAction.ComposeEmail,
        BasicDeviceAction.PickDriveDocument,
        is BasicDeviceAction.SearchLocalInbox,
        is BasicDeviceAction.CreateCalendarEvent -> null
        null -> null
    }

    private fun String.isStateFollowUp(states: Set<String>): Boolean {
        val words = split(' ')
        return words.any(states::contains) && words.all { it in states || it in setOf("turn", "switch", "it", "the") }
    }

    private fun String.containsWord(word: String): Boolean = split(' ').contains(word)

    private val OPEN_WORDS = setOf("open", "launch", "start")
    private val TORCH_WORDS = setOf("flashlight", "torch", "flash")
    private val ON_WORDS = setOf("on", "enable", "start")
    private val OFF_WORDS = setOf("off", "disable", "stop")
    private val MEDIA_WORDS = setOf("music", "audio", "song", "track", "media", "playback")
}
