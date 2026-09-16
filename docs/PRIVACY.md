# Privacy

LocalMind is designed for on-device inference. It has no cloud-model fallback and does not upload prompts or responses to an AI service.

## Data Stored On The Device

- Downloaded or imported model files in app-specific storage.
- Shared-inbox metadata, selected document URIs, and bounded text extracted from user-selected plain-text files.
- Model selection and download state.
- A completion marker containing model size and public integrity metadata.
- Explicit memories and reminder records in LocalMind's private Room database.

Chat messages are currently held in memory for the active app session and are not persisted as conversation history.

## Data Sent Elsewhere

- Model downloads contact the public model host listed in `docs/MODELS.md`.
- A confirmed WhatsApp or email handoff passes only the displayed draft to the chosen external app.
- Android Calendar receives only an event the user explicitly confirmed.
- Android's alarm and notification services receive only a reminder the user confirmed.
- No Gmail or Drive account API is currently connected.

## User Control

- Android permission prompts remain visible.
- Communication drafts require confirmation and a final send action in the external app.
- Drive content enters LocalMind only after selection in Android's document picker or an explicit share action.
- Memories can be inspected, edited, deleted, or cleared from `More options > My data`.
- Pending reminders can be inspected and deleted from `More options > My data`.
- Clearing application storage removes LocalMind's database, private preferences, inbox metadata, and downloaded models.

Do not include real personal data in bug reports. Redact screenshots, logs, prompts, contact names, and document contents.
