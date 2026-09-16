# Contributing

Thanks for helping improve LocalMind.

## Development

1. Install Android Studio, JDK 17+, and Android SDK Platform 34.
2. Open the repository root in Android Studio.
3. Let Android Studio create `local.properties` for your SDK path.
4. Run `./gradlew testDebugUnitTest assembleDebug` before submitting changes.

## Engineering Rules

- Keep inference local unless a separate cloud feature is explicit, disclosed, and opt-in.
- Treat model output as untrusted input.
- Use deterministic parsers and typed actions for device capabilities.
- Never bypass Android permissions or user confirmation.
- Never report an external action as completed without verification.
- Add tests for meaningful behavior changes.
- Document new integrations and their limitations in `docs/INTEGRATIONS.md`.

## Privacy And Repository Hygiene

Do not commit:

- API keys, OAuth credentials, access tokens, or signing keys
- Model weights or generated APK/AAB files
- Personal prompts, messages, documents, or account data
- Emulator screenshots, UI hierarchy dumps, logs, or crash dumps containing private content
- Android Studio state, local SDK paths, or generated build directories

Use synthetic fixtures in tests. Redact logs and screenshots before attaching them to an issue.

## Pull Requests

Keep changes focused. Explain user-visible behavior, safety implications, tests performed, and any remaining platform limitation.
