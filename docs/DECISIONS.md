# Architecture Decisions

## 2026-09-21: Ship SMS Insights In The Direct APK

**Status:** Implemented

**Problem:** Users need to ask naturally worded questions about their own recent SMS, including
OTPs, transactions, balances, spending, and observed accounts. These values are sensitive and
small-model generation is not a trustworthy source.

**Decision:** The open-source direct APK may request `READ_SMS` only after an explicit matching
question and an in-app explanation. Android's SMS provider supplies bounded records to typed,
deterministic parsers. Raw messages and parsed values are not persisted or sent to the LLM.

OTPs are restricted to the previous 24 hours, masked until explicit reveal, and shown in a dialog
with screenshot protection. Financial outputs are described as SMS-derived estimates rather than
live bank data.

**Trade-offs:** Bank SMS formats vary, so some valid messages will be missed and parser fixtures
must expand over time. Direct APK distribution avoids making a false Google Play availability
claim; any future Play release must re-evaluate restricted SMS permission eligibility.

## 2026-09-17: Defer ADK Kotlin Adoption Pending A Compatibility Spike

**Status:** Proposed, not implemented

**Problem:** LocalMind needs sessions, typed tool calling, confirmation, and eventually
local memory without weakening its offline and deterministic safety boundaries.

**Evidence:** The official ADK Kotlin project currently documents Android support,
LiteRT-LM on-device inference with tool calling, session services, human-in-the-loop
features, and Room-backed Android sessions. Its LiteRT-LM module also documents a JDK 21+
requirement. LocalMind currently builds with Java 17 and directly uses LiteRT-LM 0.16.1.

Sources:

- https://github.com/google/adk-kotlin
- https://github.com/google/adk-kotlin/tree/main/litertlm

**Options considered:**

1. Add ADK immediately and replace current routing.
2. Keep a permanent custom agent framework.
3. Preserve deterministic production routing and run a narrow ADK compatibility spike.

**Decision:** Choose option 3. Do not add ADK to the production dependency graph yet.

**Reasons:**

- A JDK/toolchain migration and an orchestration rewrite should not be combined blindly.
- APK size, dependency conflicts, minSdk behavior, runtime memory, and LiteRT-LM version
  alignment have not been measured in this application.
- Existing Android actions are deterministic and already have confirmation boundaries.
- The current synthetic `AgentController` is not valuable enough to force either migration
  direction without a real vertical slice.

**Spike acceptance criteria:**

- Build an isolated branch/module with the official stable ADK Kotlin artifacts.
- Confirm Android API 26 compatibility and a reproducible JDK 21 build.
- Run one local LiteRT-LM model with a no-op typed tool.
- Demonstrate confirmation/resume and Room session deletion.
- Measure dependency/APK growth, initialization memory, and latency.
- Verify message-content telemetry remains disabled and no network service is required.

**Consequence:** Milestone 1 uses simple repositories and deterministic coordinators. ADK
may replace orchestration later, but Android adapters and persistence remain independent.
