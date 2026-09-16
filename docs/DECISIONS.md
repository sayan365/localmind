# Architecture Decisions

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
