# Changelog

All notable changes will be documented in this file.

## [Unreleased]

### Added

- Native Android application with on-device LiteRT-LM chat.
- Resumable and integrity-checked local model management.
- Guarded device actions, communication drafts, Calendar creation, Drive selection, and local inbox search.
- Android/JVM unit tests and public project documentation.
- Audited current-state, architecture, model, roadmap, integration, and ADK decision documentation.
- Room-backed explicit memories with inspect, search, edit, delete, and clear flows.
- Confirmed on-device reminders with notification permission, reboot restoration, and overdue expiry.
- Android instrumentation coverage for memory persistence and reminder delivery state.
- On-request SMS insights for unread counts, recent OTPs, transactions, balances mentioned in SMS,
  observed account endings, and monthly debit totals.
- Deterministic basic arithmetic, truthful capability responses, and model-output quality checks.
- Privacy and permission status UI with direct access to Android app settings.

### Changed

- Plain-text document imports now use bounded background streaming instead of reading the complete file on the UI thread.
- The overflow menu now owns a compact `My data` surface for local memories and pending reminders.
- Lite-model chat no longer emits model-tier switching instructions for ordinary questions.
- Model generation uses lower randomness and one bounded retry when output fails quality checks.
- Standalone greetings, thanks, acknowledgements, and goodbyes are answered locally without invoking the model.

### Known Limitations

- Model-host throughput varies by network route.
- Gmail and automatic Drive account search are not connected.
- Document extraction currently supports bounded plain text only.
- Financial SMS parsing depends on known message formats and does not represent a live bank balance.
