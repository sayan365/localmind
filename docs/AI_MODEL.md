# AI Model And Runtime

Audit date: 2026-09-17

No new default model has been selected by this audit. The current catalog remains in place
until representative 4 GB hardware measurements exist.

## Current Runtime

LocalMind uses `com.google.ai.edge.litertlm:litertlm-android:0.16.1` behind the
`OnDeviceChatEngine` interface. `LiteRtChatEngine` supports initialize, send, reset,
cancel, and close. It does not currently expose streaming callbacks, runtime capabilities,
or memory statistics.

The engine uses one long-lived LiteRT-LM `Engine` and creates a fresh `Conversation` for
each turn. It reconstructs at most one relevant previous exchange in the prompt. CPU is
used for Lite and Standard; the Full entry requests GPU.

## Current Catalog

| Tier | Artifact | Size | Backend | Current role |
| --- | --- | ---: | --- | --- |
| Lite | Qwen3 0.6B INT4 LiteRT-LM | 347,251,840 bytes | CPU | Default below 8 GiB and recovery model |
| Standard | Gemma 3 1B IT INT4 | 584,417,280 bytes | CPU | Optional licensed manual import |
| Full | Gemma 4 E2B IT | 2,588,147,712 bytes | GPU | Recommended at 8 GiB or above |

Qwen has an exact SHA-256 in the catalog. The Gemma entries do not currently have catalog
digests and therefore require stronger distribution/integrity work before release.

## Quality And Context

- Lite uses a 1,280-token engine limit and 256 output tokens for direct responses.
- Standard uses 2,048 and 384.
- Full uses 4,096 and 384.
- Sampling is top-k 40, top-p 0.9, temperature 0.7, fixed seed 24.
- Selected question forms receive a bounded thinking budget; drafting uses direct mode.
- The Lite tier declines many unsupported factual questions instead of presenting guesses.
- A small deterministic resolver handles a few reported fact/math cases. It is not a
  knowledge base and must not be expanded into one-off patches as a model strategy.

## Candidates For Benchmarking

### Gemma 3n E2B IT

Primary quality candidate, not yet selected. Google publishes an official LiteRT-LM model
and describes Android support, INT4 weights, CPU/GPU/NPU paths, and multimodal capability.
Access requires acceptance of the Gemma terms. Published flagship-device results are not
evidence that it will fit or perform acceptably on the target 4 GB phone.

Source: https://huggingface.co/google/gemma-3n-E2B-it-litert-lm

### Gemma 4 E2B IT

Current Full-tier candidate with LiteRT-LM and tool-calling relevance. Its roughly 2.4 GiB
artifact plus runtime state makes it unsuitable to assume safe on a 4 GB phone without
measurement. It remains an 8 GiB-class experiment.

Source: https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm

### Gemma 3 1B IT INT4

Smaller than the E2B candidates and therefore important for 4 GB testing. Its license-gated
distribution currently creates poor setup UX, and quality still needs an app-specific eval.

Source: https://huggingface.co/litert-community/Gemma3-1B-IT

### Qwen3 0.6B INT4

Retain as the current downloadable fallback because it is compact, directly downloadable,
and already integrated. Its observed instruction-following and factual quality are below
the desired product bar.

Source: https://huggingface.co/litert-community/Qwen3-0.6B-int4

## Runtime Alternatives

| Runtime | Assessment |
| --- | --- |
| LiteRT-LM | Keep as baseline: already integrated, Android-native API, current model artifacts, CPU/GPU support, and a path to ADK tool calling |
| llama.cpp / GGUF | Valid comparison candidate if LiteRT model coverage or 4 GB performance fails; adds JNI packaging, model conversion/distribution, and a second runtime to maintain |
| ML Kit Gemini Nano | Device availability varies and current ADK Kotlin documentation says tool calling is not yet supported; not a universal offline baseline |

No second runtime should be added until a controlled benchmark shows a material advantage.

## Required Benchmark Protocol

For each candidate, record on the same physical 4 GB device:

1. Artifact and installed storage size.
2. Cold and warm load time.
3. Peak process RSS during load and generation.
4. First-response latency and total generation time.
5. Decode tokens/second when measurable.
6. Success, timeout, crash, and OOM counts across repeated runs.
7. Battery delta and device temperature over a fixed prompt suite.
8. Quality on drafting, follow-up context, extraction, calculation, and typed action proposals.

The prompt suite and raw measurements must be committed before changing automatic model
selection thresholds.

## Current Decision

Continue with LiteRT-LM and Qwen3 0.6B as the verified baseline. Benchmark Gemma 3 1B and
Gemma 3n E2B on the target phone next. Do not replace the fallback or automatically select
a gated model until licensing, download UX, integrity, RAM, and quality are all measured.
