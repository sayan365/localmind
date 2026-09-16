# Reliability

## Model Downloads

- In-progress data uses numbered part files, never the runtime model filename.
- The Hub resolver URL is opened once and its signed storage URL is reused across bounded workers.
- Approximately 8 MiB chunks reduce long-tail imbalance without excessive connection churn.
- Every part resumes from its exact on-disk byte offset.
- A download layout version invalidates incompatible partial files after upgrades.
- Activation requires the exact catalog byte count and expected SHA-256 digest.
- Cancellation removes temporary parts and download state.

Upstream throughput can still vary significantly by network route. A project-controlled model CDN is the long-term distribution solution.

## Model Runtime

- Initialization and generation run away from the Android main thread.
- Initialization has bounded timeouts and a separate recovery activity.
- Failed larger-model initialization can fall back to an already installed Lite tier.
- Hidden reasoning markers and unsupported formatting are removed before display.
- Deterministic local answers cover a small set of known factual/math failures; unsupported knowledge requests on the Lite tier are not presented as verified facts.

## Actions

- Consequential actions require confirmation.
- Calendar creation is verified through returned provider IDs.
- Media commands are described as dispatched because Android provides no completion acknowledgement.
- WhatsApp and email are described as drafts/handoffs because LocalMind cannot verify a final send.
- Tool execution has explicit states, bounded steps, typed arguments, and honest failure results.
