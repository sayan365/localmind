# Changelog

All notable changes will be documented in this file.

## [Unreleased]

### Added

- Native Android application with on-device LiteRT-LM chat.
- Resumable and integrity-checked local model management.
- Guarded device actions, communication drafts, Calendar creation, Drive selection, and local inbox search.
- Android/JVM unit tests and public project documentation.
- Audited current-state, architecture, model, roadmap, integration, and ADK decision documentation.

### Changed

- Plain-text document imports now use bounded background streaming instead of reading the complete file on the UI thread.

### Known Limitations

- Model-host throughput varies by network route.
- Gmail and automatic Drive account search are not connected.
- Document extraction currently supports bounded plain text only.
