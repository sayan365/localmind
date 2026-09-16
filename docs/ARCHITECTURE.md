# Architecture

LocalMind separates conversational inference from device actions. The language model produces text only; it does not receive an unrestricted tool bridge.

## Runtime Paths

```text
Conversation
MainActivity -> OnDeviceChatEngine -> LiteRT-LM -> formatted local response

Device action
MainActivity -> BasicDeviceActionParser -> confirmation/permission -> Android adapter -> result

Structured agent foundation
AgentController -> ToolRegistry -> risk gate -> tool -> observation/verification
```

## Components

- `MainActivity`: chat UI, model lifecycle, confirmations, permissions, and user-visible status.
- `LiteRtChatEngine`: local LiteRT-LM conversation implementation.
- `LocalModelCatalog`: supported model metadata, hardware tiers, expected sizes, and integrity digests.
- `LocalModelStore`: app-specific model storage, resumable parts, activation, and completion markers.
- `ModelDownloadService`: foreground, resumable HTTP range downloader.
- `BasicDeviceActionParser`: deterministic natural-language parser for the supported action allowlist.
- `BasicDeviceActions`: flashlight, media, volume, and allowlisted app-launch adapters.
- `AndroidCalendarConnector`: confirmed Calendar provider creation and verification.
- `WhatsAppHandoff` and `EmailHandoff`: user-controlled draft intents.
- `SharedContentStore`: local metadata/text inbox populated through Android sharing and document selection.
- `AgentController` and `ToolRegistry`: tested structured-agent foundation retained for future validated plans.

## Trust Boundaries

- Model output is untrusted and cannot execute Android APIs directly.
- Package names come from a fixed allowlist, never generated text.
- Android owns runtime permission prompts.
- Calendar writes and communication drafts require visible confirmation.
- Handoffs are reported as handoffs, not as completed sends.
- Personal prompt and response content is excluded from the activity trace.
