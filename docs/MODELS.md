# Local Model Runtime

LocalMind uses Google LiteRT-LM on Android. Inference runs inside the application process; there is no cloud-model fallback.

## Product Tiers

### Lite: Qwen3 0.6B INT4

- Repository: `litert-community/Qwen3-0.6B-int4`
- File: `qwen3_0.6b_q4_block32_ekv1280.litertlm`
- Download: 347,251,840 bytes (approximately 331 MiB).
- Integrity: SHA-256 `312ce4e6bc0816edf844b9b7e66542648a89440b54822c3581e48a19e9ff5715`.
- Intended devices: phones below 8 GB RAM and phones where a larger model cannot initialize reliably.
- Thinking mode: adaptive. Writing and drafting use direct mode; factual, mathematical, and explanatory questions receive a bounded reasoning budget. If reasoning is interrupted before a final answer, LocalMind retries once in direct mode.
- Context cache: 1,280 tokens to keep memory bounded.
- License: Apache 2.0.

This is the default downloadable tier below 8 GB RAM and the recovery tier on constrained phones. It is suitable for short drafting and simple commands, but it is not a dependable general-knowledge assistant. LocalMind additionally strips complete or interrupted reasoning sections before any response reaches the UI.

### Standard: Gemma 3 1B IT INT4

- Repository: `litert-community/Gemma3-1B-IT`
- File: `gemma3-1b-it-int4.litertlm`
- Artifact size: 584,417,280 bytes (approximately 557 MiB).
- Intended devices: 4-7 GB RAM.
- Working context: 2,048 tokens to keep memory bounded on 4 GB phones.
- Backend: CPU for broad Android and emulator compatibility.
- License: Gemma Terms of Use; the user must accept the license before downloading.

This is an optional stronger tier for 4-7 GB phones. It is not selected automatically while its repository remains gated. LocalMind opens the official model page for license acceptance and then imports the user-downloaded file. The application does not bypass the license gate or embed Hugging Face credentials. If initialization fails and Lite is already available, LocalMind restarts into the Lite model.

### Full: Gemma 4 E2B IT

- Repository: `litert-community/gemma-4-E2B-it-litert-lm`
- File: `gemma-4-E2B-it.litertlm`
- Download: 2,588,147,712 bytes.
- Intended devices: 8 GB RAM or more.
- Maximum model context: 32K; LocalMind uses a smaller working context initially to control memory.

## Selection

The app recommends Lite below 8 GiB reported RAM and Full from 8 GiB. Gemma 3 remains an explicit user choice until LocalMind has a compliant one-tap distribution source. Upgrades preserve an available Qwen model instead of silently moving the user into Gemma setup. Users may manually select and import a matching `.litertlm` file. A model is stored in app-specific storage and is never uploaded.

## Runtime Contract

- SDK: `com.google.ai.edge.litertlm:litertlm-android:0.16.1`.
- Initialization and generation run off the Android main thread.
- Each request uses a fresh native conversation. Only the immediately preceding exchange is included when deterministic relevance checks identify a follow-up; unrelated topics start with clean context. This avoids both template-delta failures and repetitive context contamination on the compact model.
- Verified local resolvers run before generation for supported factual and mathematical requests. On the Lite tier, unsupported knowledge questions are declined instead of allowing the model to invent a confident answer.
- Common LaTeX delimiters and commands in model output are normalized into readable native Android text.
- GPU model initialization has a 30-second timeout; CPU models have a 120-second cold-start limit. A minimal recovery activity can run in a separate app process, terminate a wedged inference process, and relaunch LocalMind with the persisted Lite selection. The reason is shown in chat after recovery.
- The chat composer is unavailable until a model is ready.
- Model download/import, initialization, ready, and error states are visible and recoverable.
- Public Hugging Face models use a resumable work queue of approximately 8 MiB byte ranges. LocalMind resolves the Hub URL once per download and reuses its signed Xet storage URL across 16 workers. This keeps workers useful near completion without the connection overhead caused by very small chunks.
- A range resumes at its exact byte offset after a normal network timeout. Productive partial attempts do not consume the retry budget.
- Downloads use dedicated `.part-N` and `.downloading` files. Only a complete assembly with the exact catalog size and expected SHA-256 is promoted to the runtime filename; sparse preallocated or partial files can never be initialized.
- Download progress reports transferred bytes, measured speed, estimated remaining time, and Android pending/paused state instead of showing percentage alone.
- Local model output never bypasses the deterministic tool registry, Android permissions, or confirmation gates.
- Raw native exceptions, rendered prompts, and hidden reasoning are never displayed to users.

## Limitations

- Download size, initialization time, speed, and memory use vary by device and backend.
- The 4 GB tier has a bounded context and cannot match Gemma 4 on difficult reasoning or long documents.
- Quantized INT4 models trade some output quality for lower storage and memory use.
- Gemma 3 and Lite use CPU for broad compatibility; the Full tier still requires compatible GPU acceleration.
- Local models can still make factual mistakes. The app does not present model output as verified information.
- The bundled verified-answer set is intentionally small; it is a reliability layer, not a complete offline encyclopedia or symbolic mathematics system.
- The application does not silently switch to a remote model when local inference fails.
