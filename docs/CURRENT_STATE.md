# Current State

Audit date: 2026-09-21

This document describes the repository as it exists. Planned capabilities are not listed
as implemented.

## Build

| Item | Value |
| --- | --- |
| Project | Single Android application module: `androidApp` |
| Language/UI | Kotlin with programmatic classic Android Views |
| Android Gradle Plugin | 8.3.0 |
| Kotlin plugin | 2.3.0 |
| Gradle wrapper | 8.7 |
| Java target | 17 |
| compileSdk / targetSdk | 34 / 34 |
| minSdk | 26 |
| Version | 0.1.0 (versionCode 1) |
| Production dependencies | LiteRT-LM Android 0.16.1; Room 2.8.5 |
| Test dependencies | JUnit 4.13.2; AndroidX Test runner 1.7.0; ext.junit 1.3.0 |
| Debug APK | Generated at `androidApp/build/outputs/apk/debug/androidApp-debug.apk` |

The current verification run completed `testDebugUnitTest`, `assembleDebug`, and
`connectedDebugAndroidTest`. JVM tests pass, and three Room/reminder integration tests pass
on the API 36 `Medium_Phone` emulator. Model inference, battery, thermal behavior, and a
representative 4 GB physical-device run remain unmeasured for this milestone.

## What Works In Code

- On-device text generation through LiteRT-LM after a compatible `.litertlm` file is installed.
- Model setup, manual import, selection, initialization timeout, and Lite fallback.
- Resumable segmented Qwen download with exact size and SHA-256 verification.
- Session-only chat, one-exchange relevance heuristics, response copying, and basic formatting.
- Fixed app launch allowlist for WhatsApp, Gmail, Drive, Calendar, YouTube, YouTube Music, and Spotify.
- Flashlight control with runtime permission and torch callback verification.
- Media-key dispatch and music-stream volume adjustment.
- Confirmed WhatsApp and email draft handoffs; the external app performs the final send.
- Confirmed all-day Calendar event creation and provider-ID verification.
- User-controlled document selection and local search over stored plain text, MIME type, and URI metadata.
- Android share target for text and URI references.
- Room-backed explicit memories with local search, edit, delete, and clear controls.
- Confirmed local reminders using `AlarmManager`, notifications, and reboot restoration.
- A `My data` dialog that reports and exposes the implemented memory and reminder stores.
- On-demand SMS insights for recent OTPs, latest transactions, balances mentioned in messages,
  account endings, and monthly debit totals.
- A deterministic capability answer, basic arithmetic evaluator, and model-response quality gate.
- A privacy/permissions dialog with a direct route to Android's app settings.
- In-memory activity trace that avoids prompt and response bodies.

## Partial Or Experimental

- The structured agent and tool registry are tested against synthetic Gmail, Drive, and Calendar data but are not wired into the production UI.
- Model conversation context includes at most the immediately relevant previous exchange and is capped by character count, not token count.
- The local inbox extracts only `text/*` content. PDF, Office, image, and OCR extraction do not exist.
- Shared URIs can become unreadable when the source does not grant persistent access.
- Calendar parsing supports `today`, `tomorrow`, or a normalized ISO-like date and creates all-day events only.
- Markdown support is intentionally small: headings, bullets, bold, inline code, and fenced code.
- Model integrity is cryptographically verified only when a catalog SHA-256 is present. The optional Gemma entries currently rely on exact byte size.

## Not Implemented

- Durable conversation history, editing, regeneration, conversation search, or stop-generation UI.
- Semantic memory retrieval, embeddings, vector search, AppSearch, or document chunking.
- PDF/Office extraction, OCR, photo indexing, image understanding, contacts, or notes.
- Voice input, offline speech recognition, or text-to-speech.
- Full privacy/settings center or per-source indexing controls.
- Authenticated Gmail search, automatic Drive search, or WhatsApp history access.
- Model-driven production tool calling.
- Dark theme, formal accessibility audit, benchmark tests, release signing, or CI.

## Known Problems And Risks

1. Qwen3 0.6B has limited factual and reasoning quality. Lower sampling randomness, a quality
   gate, deterministic arithmetic, and a few verified answers reduce known failures but cannot
   prove arbitrary world-knowledge answers correct.
2. The download path uses 16 concurrent HTTP ranges. It is resumable, but throughput, battery cost, and server behavior are not adaptively controlled or benchmarked.
3. Model assembly temporarily needs space for both all part files and the assembled model. Verification then reads the complete file again.
4. Plain-text document imports are now streamed off the UI thread and capped at 250,000 characters, but cancellation and encoding selection are not exposed.
5. Up to 50 large text records are serialized as one SharedPreferences JSON value. This is not suitable for a real document index.
6. `MainActivity` remains large and owns UI, model lifecycle, download presentation, permissions, persistence calls, and action routing.
7. Rotation or process death loses chat and pending UI state. There is no `ViewModel` or saved-state layer.
8. Manual model selection is not blocked by measured available memory; incompatibility is handled only after initialization fails or times out.
9. The Lite model can be recommended on devices reporting less than its declared 4 GiB minimum because the catalog has no lower tier.
10. Device tests cover Room persistence and reminder delivery state, but not external intents, providers, permission denial, service restart, cancellation, or model runtime behavior.
11. Reminder scheduling is intentionally inexact and may be delayed by Android battery management.
12. No representative 4 GB performance baseline exists yet.

## Permissions

| Permission | Current use | Runtime prompt |
| --- | --- | --- |
| `INTERNET` | Download public model files | No |
| `FOREGROUND_SERVICE` | Keep model download active | No |
| `FOREGROUND_SERVICE_DATA_SYNC` | Declare the download service type | No |
| `CAMERA` | Flashlight control | Yes, at first use |
| `MODIFY_AUDIO_SETTINGS` | Media volume adjustment and media keys | No |
| `READ_CALENDAR` | Select and verify Calendar provider data | Yes, after confirmation |
| `WRITE_CALENDAR` | Insert a confirmed event | Yes, after confirmation |
| `POST_NOTIFICATIONS` | Show confirmed local reminders | Yes, when the first reminder is created on Android 13+ |
| `READ_SMS` | Answer an explicit OTP, transaction, balance, account, or spending question | Yes, after an in-app explanation |
| `RECEIVE_BOOT_COMPLETED` | Restore future pending reminders after restart | No |

## Persistence And Privacy

There is no account, analytics SDK, telemetry SDK, or cloud-model fallback. Network use is
limited to model download URLs and user-initiated external app behavior. Chat is not
persisted. Explicit memories and reminder records are stored in a private Room database.
Clearing app storage removes the database, preferences, inbox metadata, caches, and downloaded
models. External applications retain anything the user sends through them.
SMS bodies are queried on demand and kept only in process memory while the requested analysis
runs. They are not copied to Room, preferences, the model prompt, or the activity trace.

## Test Coverage

JVM tests cover deterministic action parsing, the synthetic structured-agent workflow,
model catalog rules, segmented-download boundaries, prompt-context heuristics, reasoning
cleanup, verified answers, formatting normalization, local inbox ranking, memory parsing,
reminder parsing, and memory bounds. Android instrumentation covers Room memory CRUD,
reminder delivery status, and overdue-reminder expiry. The downloader, model runtime,
lifecycle recovery, external providers, and permission-denial UI still lack device coverage.
