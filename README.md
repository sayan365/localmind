# LocalMind

LocalMind is a privacy-first Android assistant that runs a small language model on the device. Conversation stays local, while phone and app actions pass through deterministic parsers, Android permission checks, explicit confirmation, and result verification where the platform supports it.

> Status: early Android preview. The core local chat and guarded action flows work, but this is not yet a production release.

## Features

- On-device chat using [LiteRT-LM](https://github.com/google-ai-edge/LiteRT-LM), with no cloud AI fallback.
- RAM-aware model selection and manual `.litertlm` import.
- Resumable, integrity-checked Qwen3 0.6B model download.
- Selectable and copyable assistant responses.
- Flashlight, media transport, music volume, and allowlisted app-launch actions.
- Confirmed WhatsApp and email draft handoffs. The user performs the final send.
- Confirmed Android Calendar event creation with provider-ID verification.
- User-controlled Drive document selection and a device-local shared inbox.
- Deterministic local inbox search for imported text and metadata.
- Explicit on-device memories with search, edit, deletion, and clear controls.
- Confirmed local reminders with notification delivery and reboot restoration.
- On-request SMS insights for unread counts, recent OTPs, bank transactions, balances mentioned in messages, account endings, and monthly spending.
- A response quality gate that rejects model-template leaks, repeated answers, and false action-completion claims.
- Deterministic local arithmetic for basic expressions.
- Private activity trace that does not record prompts or response content.

## Safety Model

Model output is untrusted text. It cannot directly call Android APIs, choose arbitrary packages, grant permissions, or confirm its own actions.

```text
User request
  -> deterministic action parser
  -> permission and confirmation gate
  -> Android adapter or app handoff
  -> verified result when the platform exposes one

Ordinary conversation
  -> LiteRT-LM
  -> response quality gate
  -> on-device response or honest fallback

Sensitive SMS question
  -> runtime permission
  -> bounded Android SMS query
  -> deterministic local parser
  -> cited result; raw SMS never enters the model
```

Consequential actions remain visible and user-controlled. WhatsApp and email drafts are never sent automatically.

## Requirements

- Android Studio with JDK 17 or newer
- Android SDK Platform 34
- Android 8.0 (API 26) or newer device/emulator
- Internet access for the first model download

The target physical test profile is a 4 GB RAM Android phone. More device coverage is still needed.

## Build

Clone the repository and open its root directory in Android Studio, or build from a terminal:

```bash
./gradlew testDebugUnitTest assembleDebug
```

On Windows:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

The debug APK is generated at:

```text
androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

Install it on an attached device with Android Studio or ADB:

```bash
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

Do not commit `local.properties`; Android Studio generates it for the local SDK path.

## Models

The default constrained-device tier is the Apache-2.0 Qwen3 0.6B INT4 LiteRT-LM artifact. The app verifies its exact byte size and SHA-256 digest before activation.

Model files are not included in this repository or APK. They remain in app-specific device storage after download or import. Hugging Face Xet throughput can vary by network route; interrupted chunks are resumable.

Gemma tiers are optional and may require license acceptance and manual import. See [docs/MODELS.md](docs/MODELS.md) for exact artifacts, selection rules, and limitations.

## Integration Boundaries

| Capability | Current behavior |
| --- | --- |
| WhatsApp | Opens a confirmed draft; user selects/reviews the chat and sends |
| Email | Opens a confirmed `mailto:` draft; user sends |
| Calendar | Creates confirmed all-day events and verifies provider IDs |
| Reminders | Schedules confirmed local notifications with Android `AlarmManager` |
| Memories | Stores only explicit `Remember that...` requests in a local Room database |
| Drive | Opens Android's document picker; user explicitly selects a file |
| Gmail/Drive search | Test fixtures only; real account search is not connected |
| Flashlight | Uses `CameraManager` with runtime permission and callback verification |
| Media | Dispatches Android media keys; Android provides no completion acknowledgement |
| SMS insights | Reads recent inbox messages only after permission and only when asked; raw messages are not stored or passed to the model |

LocalMind does not scrape private app databases, inject arbitrary UI input, bypass Android permissions, or silently connect a cloud model.

## Project Structure

```text
androidApp/src/main/       Android application and device adapters
androidApp/src/test/       JVM unit tests
androidApp/src/androidTest/ Emulator/device integration tests
androidApp/schemas/        Versioned Room database schemas
docs/                      Architecture, models, integrations, privacy, and security
gradle/                    Gradle wrapper
```

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [Current state](docs/CURRENT_STATE.md)
- [Roadmap](docs/ROADMAP.md)
- [AI model and runtime](docs/AI_MODEL.md)
- [Models](docs/MODELS.md)
- [Integrations](docs/INTEGRATIONS.md)
- [Reliability](docs/RELIABILITY.md)
- [Privacy](docs/PRIVACY.md)
- [Security](docs/SECURITY.md)
- [Architecture decisions](docs/DECISIONS.md)

## Known Limitations

- Qwen3 0.6B is compact enough for constrained phones but has limited factual reliability and context capacity.
- Upstream model-download throughput is not consistent across all networks.
- Gmail inbox search and automatic Drive search require a future OAuth integration.
- PDF, Office document, and image text extraction are not implemented yet.
- WhatsApp does not expose supported APIs for reading chat history or unattended sending.
- SMS balance and spending answers are estimates from available notification messages, not live bank records or financial advice.
- OTP lookup searches only the last 24 hours and reveals the result in a secure, masked dialog.
- Calendar editing, deletion, recurrence, and conflict detection are not implemented.
- Reminder language currently requires `today`, `tomorrow`, or an ISO date plus an explicit time.

## Contributing

Read [CONTRIBUTING.md](CONTRIBUTING.md) before opening a pull request. Please do not include model weights, credentials, personal messages, emulator captures, or generated APKs.

## License

LocalMind source code is available under the [MIT License](LICENSE). Downloaded models retain their own licenses and terms.
