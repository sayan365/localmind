# Integrations

## Integration Boundary

LocalMind uses public Android capabilities for its production runtime. Basic device
actions, app launches, confirmed message and email handoffs, calendar creation,
the system document picker, and local inbox search operate on the device without a
cloud AI service.

The project also contains deterministic Gmail, Drive, and Calendar fixtures for JVM
tests. These fixtures use invented data and are never presented as connected account
results in the Android UI. Authenticated Gmail inbox search and automatic Drive search
are not implemented.

Before adding a connected service, document its authentication flow, scopes, API
capabilities, rate limits, failure modes, data handling, and verification method here.

## Capability Matrix

| Integration | Read/search | Write/action | Offline behavior | Current status |
| --- | --- | --- | --- | --- |
| Android apps | No private app data | Launch fixed allowlisted packages | Yes, when installed | Implemented |
| Flashlight | Torch callback state only | Set torch mode | Yes | Implemented; camera permission required |
| Media | No playback metadata | Dispatch media keys; adjust music volume | Yes | Implemented; media-key completion cannot be verified |
| WhatsApp | No chats, contacts, or documents | Open a text draft/share handoff | Draft creation is local; sending depends on WhatsApp | Implemented with confirmation and user final send |
| Email/Gmail | No inbox access | Open a `mailto:` draft | Draft creation is local; sending requires an email app/network | Implemented with confirmation and user final send |
| Drive/files | User-selected URI and plain text only | Import a selected reference into the local inbox | Selected locally available content can be used offline | Partial; no automatic Drive search or PDF extraction |
| Calendar | Find a writable calendar and verify inserted IDs | Create a confirmed all-day event | Provider behavior depends on the configured calendar | Implemented for creation only |
| Local reminders | Read pending reminder records | Schedule/delete confirmed notifications | Yes | Implemented with inexact alarms and reboot restoration |
| Local memories | Search explicit saved text | Add, edit, delete, or clear | Yes | Implemented with Room; no embeddings |
| Local inbox | Search stored text, MIME type, and URI metadata | Add through Share or the document picker | Yes | Implemented, not a document index or RAG system |
| Gmail API | None | None | No | Not connected |
| Drive API | None | None | No | Not connected |

## Basic Android Actions

LocalMind supports a deterministic, allowlisted first action package:

- Launch WhatsApp, Gmail, Drive, Calendar, YouTube, YouTube Music, or Spotify when installed.
- Turn the flashlight on or off through `CameraManager`, with runtime camera permission and torch callback verification.
- Dispatch play, pause, stop, next, and previous media keys to Android's current media consumer.
- Raise, lower, mute, or unmute the music stream and verify the resulting volume value.

These commands are parsed before model generation and can run without a model file. App launching is considered successfully handed off only when Android accepts the launch intent. Media-key dispatch has no public completion acknowledgement, so LocalMind reports that the command was sent rather than claiming playback changed. The app does not use Accessibility Service, inject arbitrary UI input, enumerate all installed packages, or allow model-generated package names.

The parser retains only the immediately relevant successful basic action, allowing follow-ups such as `turn it off`, `pause it`, or `open it again`. An unrelated message or New chat clears that action context. A context-free command such as `turn it off` asks what should be controlled instead of guessing.

## Local Model Runtime

LiteRT-LM is the only inference runtime. LocalMind defaults to directly downloadable Qwen3 0.6B below 8 GB RAM, offers CPU-backed Gemma 3 1B as an optional licensed import, and supports Gemma 4 E2B on devices with at least 8 GB RAM. Model files remain in app-specific device storage. See `docs/MODELS.md` for capabilities, selection, licensing, and limitations.

Model output is untrusted input to deterministic application code. It cannot grant permissions, confirm its own proposed action, or directly call Android providers.

## Android Calendar

Implemented through Android's `CalendarContract` content provider. LocalMind requests `READ_CALENDAR` and `WRITE_CALENDAR` only when the user confirms proposed reminders. It selects a writable calendar, creates all-day events, then queries the returned provider IDs to verify that they exist.

Limitations:

- The device must have at least one writable calendar, usually supplied by a configured calendar account.
- LocalMind cannot create or bypass a missing calendar account.
- Partial provider failure is reported honestly; inserted events are not silently described as rolled back.

Natural-language requests containing a supported date (`today`, `tomorrow`, or an ISO date) are converted into a visible proposal. LocalMind requests permission only after confirmation, creates the all-day event, and verifies its provider ID before reporting success.

## WhatsApp

Implemented local surfaces:

- Share target: WhatsApp can share text, images, or files into LocalMind's device-only inbox.
- Send handoff: LocalMind can prepare text and open WhatsApp when installed, or Android's share chooser otherwise. The user reviews and performs the final send.

Limitations:

- LocalMind does not read WhatsApp's private database or scrape chat history.
- It does not reverse engineer WhatsApp or bypass Android app isolation.
- A share URI may become unreadable after the source app revokes access; the MVP stores its reference and metadata, not a hidden permanent copy.
- Recipient selection and final sending remain visible, user-controlled WhatsApp actions.

LocalMind can also parse explicit requests such as `Send a WhatsApp message to Rahul saying ...`, show the exact draft for confirmation, and open the WhatsApp send surface. A contact name is shown as a hint only; Android's public share intent cannot securely select a private WhatsApp contact by display name.

## Gmail And Drive Handoffs

- An explicit email request containing an email address and message body becomes a confirmed `mailto:` draft. The user reviews and sends it in Gmail or another email app.
- A Drive document request opens Android's system document picker. Only the document selected by the user is added to LocalMind's private inbox. Plain-text files are read locally up to a bounded size; other formats are retained as references until format-specific extraction is implemented.
- Explicit `find ... in my local inbox` requests search locally stored text, MIME metadata, and retained document references without sending content to a server.
- Gmail inbox search and unattended Drive search still require authenticated Google APIs. LocalMind does not scrape either application's private UI or data.

Full UI automation through an Accessibility Service is intentionally outside this MVP because it is fragile, permission-sensitive, and inappropriate as a hidden general-purpose control path.
