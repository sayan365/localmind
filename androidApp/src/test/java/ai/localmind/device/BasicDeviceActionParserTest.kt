package ai.localmind.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class BasicDeviceActionParserTest {
    @Test
    fun parsesAllowlistedAppLaunches() {
        assertEquals(BasicDeviceAction.OpenApp(SupportedApp.WHATSAPP), BasicDeviceActionParser.parse("Open WhatsApp"))
        assertEquals(BasicDeviceAction.OpenApp(SupportedApp.DRIVE), BasicDeviceActionParser.parse("launch Google Drive"))
        assertEquals(BasicDeviceAction.OpenApp(SupportedApp.YOUTUBE_MUSIC), BasicDeviceActionParser.parse("start YouTube Music"))
    }

    @Test
    fun parsesExplicitTorchStates() {
        assertEquals(BasicDeviceAction.SetTorch(true), BasicDeviceActionParser.parse("turn on the flashlight"))
        assertEquals(BasicDeviceAction.SetTorch(false), BasicDeviceActionParser.parse("switch torch off"))
        assertNull(BasicDeviceActionParser.parse("flashlight"))
    }

    @Test
    fun parsesMediaAndVolumeCommands() {
        assertEquals(BasicDeviceAction.Media(MediaCommand.PAUSE), BasicDeviceActionParser.parse("pause the music"))
        assertEquals(BasicDeviceAction.Media(MediaCommand.NEXT), BasicDeviceActionParser.parse("skip this song"))
        assertEquals(BasicDeviceAction.Volume(VolumeCommand.UP), BasicDeviceActionParser.parse("increase volume"))
        assertEquals(BasicDeviceAction.Volume(VolumeCommand.MUTE), BasicDeviceActionParser.parse("mute the volume"))
    }

    @Test
    fun doesNotTurnOrdinaryChatIntoActions() {
        assertNull(BasicDeviceActionParser.parse("How does a flashlight work?"))
        assertNull(BasicDeviceActionParser.parse("Write a message about music"))
        assertNull(BasicDeviceActionParser.parse("Open my report and summarize it"))
    }

    @Test
    fun resolvesImmediateFlashlightFollowUpsFromContext() {
        val previous = BasicDeviceAction.SetTorch(true)

        assertEquals(BasicDeviceAction.SetTorch(false), BasicDeviceActionParser.parse("turn off", previous))
        assertEquals(BasicDeviceAction.SetTorch(false), BasicDeviceActionParser.parse("turn it off", previous))
        assertEquals(BasicDeviceAction.SetTorch(true), BasicDeviceActionParser.parse("switch it on", previous))
    }

    @Test
    fun contextFreeStateCommandsAskForADeviceInsteadOfGuessing() {
        assertNull(BasicDeviceActionParser.parse("turn it off"))
        assertEquals(true, BasicDeviceActionParser.isAmbiguousStateCommand("turn it off"))
    }

    @Test
    fun parsesConfirmedWhatsAppHandoffsWithoutLosingMessagePunctuation() {
        assertEquals(
            BasicDeviceAction.SendWhatsApp("I'm running 10 minutes late.", "Rahul"),
            BasicDeviceActionParser.parse("Send a WhatsApp message to Rahul saying I'm running 10 minutes late.")
        )
        assertNull(BasicDeviceActionParser.parse("Write a WhatsApp message for Rahul"))
    }

    @Test
    fun parsesCalendarDatesDeterministically() {
        val clock = Clock.fixed(Instant.parse("2026-09-16T08:00:00Z"), ZoneOffset.UTC)
        assertEquals(
            BasicDeviceAction.CreateCalendarEvent("Team meeting", java.time.LocalDate.parse("2026-09-17")),
            BasicDeviceActionParser.parse("Add team meeting to calendar tomorrow", clock = clock)
        )
        assertEquals(
            BasicDeviceAction.CreateCalendarEvent("Meeting", java.time.LocalDate.parse("2026-09-17")),
            BasicDeviceActionParser.parse("Schedule a meeting tomorrow", clock = clock)
        )
    }

    @Test
    fun parsesEmailAndUserControlledDriveHandoffs() {
        assertEquals(
            BasicDeviceAction.ComposeEmail("hr@example.com", "I need leave today."),
            BasicDeviceActionParser.parse("Email hr@example.com saying I need leave today.")
        )
        assertEquals(BasicDeviceAction.PickDriveDocument, BasicDeviceActionParser.parse("Find a document in Drive"))
        assertEquals(BasicDeviceAction.SearchLocalInbox("admission letter"), BasicDeviceActionParser.parse("Find admission letter in my local inbox"))
    }
}
