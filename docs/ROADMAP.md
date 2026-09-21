# Roadmap

LocalMind will evolve through small buildable milestones. Dates are intentionally omitted
until physical-device measurements establish realistic scope.

## Milestone 0: Audited Baseline

Status: complete

- Document the current runtime, model, tools, storage, permissions, and known failures.
- Keep the existing build green.
- Record ADK and model decisions without adding dependencies.

Exit criteria: documentation matches code; JVM tests and debug assembly pass.

## Milestone 1: Durable Local Foundations

Status: complete in code; physical-device acceptance remains

- Introduce a small repository boundary and Room database for explicit memories and reminders.
- Implement add, list, edit, delete, and clear for memories without embeddings initially.
- Implement deterministic date/time parsing and Android reminder scheduling.
- Add visible confirmation for reminder creation and deletion.
- Add a minimal privacy/data screen showing only implemented stores and controls.
- Move large selected-file reads off the UI thread and stream to a bounded size.
- Add JVM tests and focused Android instrumentation tests for persistence and reminder flows.

Exit criteria: with networking disabled, a user can explicitly save a fact, retrieve it,
inspect/delete it, and create a verifiable local reminder without an LLM-generated tool call.

Implemented verification: JVM parsing/ranking tests, API 36 Room and alarm-receiver tests,
and live emulator inspection of memory creation and `My data`. A 4 GB physical-device pass
is still required before calling this production-ready.

## Milestone 2: Model And Runtime Benchmark

- Add opt-in debug measurements for load time, first response latency, generation time,
  tokens per second when exposed by the runtime, and process memory.
- Benchmark Qwen3 0.6B, Gemma 3 1B, Gemma 3n E2B, and Gemma 4 E2B on a representative
  4 GB phone before changing defaults.
- Measure cold and warm runs, failure rate, storage, battery, and thermal behavior.
- Select a licensed distribution approach and retain a recovery model.

Exit criteria: `AI_MODEL.md` contains reproducible device measurements and a justified
default/fallback decision.

## Milestone 1.1: Reliable Assistant And SMS Insights

Status: implemented in code; physical-device format coverage remains

- Route capability questions and sensitive SMS questions before model generation.
- Add deterministic transaction, balance, account, monthly spending, OTP, and arithmetic handling.
- Keep raw SMS and OTP values outside model prompts and persistent LocalMind storage.
- Add response validation and remove model-tier switching messages from ordinary chat.
- Expose permission state and Android revocation controls.

Exit criteria: varied phrasing resolves to typed local queries, denied permissions recover cleanly,
and a physical phone validates representative bank and OTP formats without network access.

## Milestone 3: Agent Foundation

- Complete a JDK 21 and APK/RAM compatibility spike for ADK Kotlin.
- Compare ADK LiteRT-LM tool calling with the existing deterministic coordinator.
- Keep Android tools typed, independently testable, and policy-gated.
- Add persistent session state only after its privacy and deletion behavior are defined.
- Never allow model text to bypass validation or confirmation.

Exit criteria: one local model can propose a typed action, the app validates it, and a
confirmed test action executes with an auditable structured result.

## Milestone 4: Local Documents

- Replace SharedPreferences document storage with bounded metadata and file records.
- Add streaming plain-text extraction, then PDF extraction, then opt-in OCR.
- Chunk and index extracted text locally.
- Start with keyword retrieval; add local embeddings only if benchmarks justify them.
- Display source citations and provide an Open file action.

Exit criteria: a user-selected bill can be found and summarized offline with a source link.

## Milestone 5: Vertical Slice

- Combine local document retrieval, explicit memory, and Android reminders.
- Support: find a selected bill, report the amount, save it when asked, and propose a reminder.
- Add failure recovery and a formal 4 GB acceptance run.

Exit criteria: the complete electricity-bill workflow works in airplane mode without OOM
on the target 4 GB test device.

## Later Milestones

- Photo metadata and user-controlled OCR indexing.
- Conversation history, search, regenerate, edit, share, and stop generation.
- Offline speech input and TTS after device/runtime evaluation.
- Authenticated external integrations one at a time, with scopes and consent documented.
- Accessibility, dark theme, onboarding, release hardening, CI, and distribution.

## Explicit Non-Goals

- Hidden cloud inference or mandatory accounts.
- Reading private app databases.
- Unattended message or email sending.
- General UI automation through Accessibility Service.
- Loading every model, index, and ML component at once.
