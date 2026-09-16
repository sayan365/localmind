# Development Plan

## Implemented Preview

- Native Android chat interface and local LiteRT-LM inference.
- Qwen3 0.6B constrained-device tier with resumable, integrity-checked download.
- Optional Gemma model import paths.
- Deterministic flashlight, media, volume, and allowlisted app-launch actions.
- Confirmed WhatsApp and email drafts.
- Confirmed and verified Android Calendar event creation.
- Share target, Drive document picker, local inbox, and deterministic inbox search.
- Unit coverage for the structured agent foundation, action parsing, model configuration, response cleanup, and local search.

## Next Priorities

1. Benchmark download, initialization, peak memory, and generation on multiple physical 4 GB phones.
2. Move the Apache-2.0 Lite model to a stable project-controlled CDN.
3. Add durable local conversation history with user-controlled deletion.
4. Add local PDF, Office document, and image text extraction.
5. Expand Calendar support to timed, recurring, editable, and deletable events.
6. Add authenticated Gmail and Drive adapters after OAuth scopes and consent behavior are documented.
7. Validate any model-produced action proposal against the same deterministic typed-action layer.
8. Add instrumentation tests, accessibility testing, release signing, and Play distribution configuration.

## Non-Goals

- Hidden cloud inference fallback.
- Reverse engineering private app storage.
- Unattended communication sending.
- Permission bypass or arbitrary Accessibility Service automation.
