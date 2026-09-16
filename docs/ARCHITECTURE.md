# Architecture

Last audited: 2026-09-17

LocalMind is a single-module native Android application. It uses classic Android Views
created in Kotlin code; it does not use Compose, a backend, or a cloud inference service.

## Current Runtime

```text
MainActivity
  |-- deterministic request parsing
  |     |-- app launch / flashlight / media / volume
  |     |-- confirmed WhatsApp and email handoffs
  |     |-- confirmed Calendar provider write + verification
  |     |-- system document picker + local inbox search
  |     `-- Room memories + AlarmManager reminders
  |
  `-- ordinary conversation
        `-- OnDeviceChatEngine
              `-- LiteRtChatEngine
                    `-- LiteRT-LM Engine / one Conversation per turn
```

`BasicDeviceActionParser` runs before model generation. Model text cannot invoke Android
APIs. Supported actions are represented by a sealed type and routed explicitly by
`MainActivity`.

## Components

| Component | Current responsibility |
| --- | --- |
| `MainActivity` | Builds the UI, owns transient chat state, routes actions, requests permissions, and manages the model lifecycle |
| `LiteRtChatEngine` | Initializes LiteRT-LM, builds bounded prompts, generates responses, and removes hidden reasoning markers |
| `LocalModelCatalog` | Declares model files, sizes, memory recommendations, context limits, and backends |
| `LocalModelStore` | Stores models, download parts, integrity markers, and model selection |
| `ModelDownloadService` | Runs foreground HTTP range downloads and assembles model files |
| `BasicDeviceActionParser` | Maps a narrow set of user phrases to typed Android actions |
| `BasicDeviceActions` | Implements app launch, flashlight, media-key, and volume operations |
| `AndroidCalendarConnector` | Creates all-day Calendar provider events and verifies returned IDs |
| `WhatsAppHandoff` / `EmailHandoff` | Creates user-controlled external draft intents |
| `SharedContentStore` | Keeps up to 50 shared-item records in SharedPreferences |
| `LocalDataRepository` | Owns bounded memory CRUD/search and reminder persistence |
| `LocalDataDatabase` | Stores versioned Room memory and reminder records |
| `ReminderScheduler` | Schedules/cancels alarms; receivers notify, update status, and restore after reboot |
| `ResponseFormatter` | Renders a small Markdown subset and normalizes common math markup |

## Structured Agent Foundation

`AgentController`, `AgentStateMachine`, `ToolRegistry`, and `EventTrace` are a tested
prototype for typed plans, risk levels, confirmation, and verification. They currently
drive only synthetic JVM fixtures and are not connected to the production chat UI.
Production actions use the simpler sealed-action path above.

This distinction matters: the application does not yet have model-driven tool calling.

## State And Storage

- Chat messages and action context exist only in the current `MainActivity` process.
- Model selection and download state use `local_models` SharedPreferences.
- Shared inbox records use `shared_content` SharedPreferences.
- Model files and range parts use app-specific external files storage.
- LiteRT-LM cache files use the app cache directory.
- Selected document URIs may have persistable read permission when the provider grants it.
- Explicit memories and reminder state use the private `localmind.db` Room database.
- There is no conversation store, embedding index, semantic memory, or document file index.

## Concurrency And Lifecycle

- LiteRT-LM initialization and generation use a single-thread executor.
- File and Calendar work use a single activity-owned executor.
- Model cleanup uses a cached executor.
- Downloads use a foreground service and a fixed 16-thread range worker pool.
- The UI polls persisted download state once per second.
- Activity recreation does not preserve the active conversation or in-flight UI state.

## Trust Boundaries

- Model output is untrusted text and cannot execute a tool directly.
- App packages are selected from a fixed allowlist.
- Android owns runtime permission dialogs.
- Communication handoffs and Calendar writes require visible confirmation.
- Reminder creation and deletion require visible confirmation; notification permission remains Android-controlled.
- A handoff is never reported as a completed send.
- Calendar success requires a provider query for the inserted event ID.
- Imported files and shared URIs are untrusted input.
- There is no arbitrary shell execution, Accessibility Service, or private app-data access.

## Recommended Direction

Keep three replaceable boundaries:

```text
Chat/UI -> Agent coordinator -> LocalModelEngine
                         |----> Typed ToolRegistry -> Android adapters
                         `----> Local repositories -> Room / files / search index
```

The next architecture step is to extract activity-owned state into small lifecycle-aware
coordinators while preserving the repository and deterministic action adapters. ADK adoption
is a separate compatibility milestone recorded in `DECISIONS.md`.
