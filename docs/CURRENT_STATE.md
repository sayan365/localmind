# Current State

Audit date: 2026-09-17

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
| Production dependencies | LiteRT-LM Android 0.16.1 only |
| Test dependency | JUnit 4.13.2 |
| Debug APK | 50,082,056 bytes in the latest local build |

The last audit build completed `testDebugUnitTest` and `assembleDebug`. It contained 32
passing JVM tests in four suites. No Android device was connected during this audit, so
instrumentation, startup, model load, inference, memory, battery, and thermal behavior
were not measured.

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
- User-controlled memory CRUD or semantic memory retrieval.
- Room/SQLite, embeddings, vector search, AppSearch, or document chunking.
- PDF/Office extraction, OCR, photo indexing, image understanding, contacts, or notes.
- Android reminders/alarms and notification permission UX.
- Voice input, offline speech recognition, or text-to-speech.
- Privacy/settings center, per-source indexing controls, or in-app data deletion controls.
- Authenticated Gmail search, automatic Drive search, or WhatsApp history access.
- Model-driven production tool calling.
- Dark theme, formal accessibility audit, instrumentation tests, benchmark tests, release signing, or CI.

## Known Problems And Risks

1. Qwen3 0.6B has limited factual and reasoning quality. A tiny hard-coded resolver covers only a few previously observed failures.
2. The download path uses 16 concurrent HTTP ranges. It is resumable, but throughput, battery cost, and server behavior are not adaptively controlled or benchmarked.
3. Model assembly temporarily needs space for both all part files and the assembled model. Verification then reads the complete file again.
4. Plain-text document imports are now streamed off the UI thread and capped at 250,000 characters, but cancellation and encoding selection are not exposed.
5. Up to 50 large text records are serialized as one SharedPreferences JSON value. This is not suitable for a real document index.
6. `MainActivity` is over 700 lines and owns UI, model lifecycle, download presentation, permissions, persistence calls, and action routing.
7. Rotation or process death loses chat and pending UI state. There is no `ViewModel` or saved-state layer.
8. Manual model selection is not blocked by measured available memory; incompatibility is handled only after initialization fails or times out.
9. The Lite model can be recommended on devices reporting less than its declared 4 GiB minimum because the catalog has no lower tier.
10. The app has no `POST_NOTIFICATIONS` flow on Android 13+, so foreground download notification visibility needs device verification.
11. There are no device tests for intents, providers, permissions, service restart, cancellation, or model runtime behavior.
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

## Persistence And Privacy

There is no account, analytics SDK, telemetry SDK, or cloud-model fallback. Network use is
limited to model download URLs and user-initiated external app behavior. Chat is not
persisted. Clearing app storage removes preferences, inbox metadata, caches, and downloaded
models. External applications retain anything the user sends through them.

## Test Coverage

JVM tests cover deterministic action parsing, the synthetic structured-agent workflow,
model catalog rules, segmented-download boundaries, prompt-context heuristics, reasoning
cleanup, verified answers, formatting normalization, and local inbox ranking. Android APIs,
the downloader, model runtime, lifecycle, UI, storage failure, and permissions are not
covered by instrumentation tests.
