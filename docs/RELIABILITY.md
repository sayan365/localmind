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
- Deterministic local answers cover a small set of known factual/math failures. Arbitrary
  world-knowledge answers from the Lite tier remain a documented quality limitation.
- Model output is checked for template leakage, repeated paragraphs, repetition of an unrelated
  prior answer, and false action-completion claims. One clean retry is allowed before an honest fallback.
- Basic arithmetic is evaluated deterministically instead of delegated to the model.

## SMS Insights

- SMS queries are bounded to 1,000 rows, a feature-specific lookback period, and 2,000 characters per body.
- OTP lookup accepts only messages with explicit OTP/security-code context and a 4-8 digit code.
- Copied OTP values are cleared from the clipboard after 60 seconds when they have not already changed.
- Financial results come from deterministic parsing and include source timing where applicable.
- Balance responses are labeled as the latest balance mentioned in SMS, not a live balance.
- Monthly spending reports its message count and warns about incomplete message coverage.

## Actions

- Consequential actions require confirmation.
- Calendar creation is verified through returned provider IDs.
- Media commands are described as dispatched because Android provides no completion acknowledgement.
- WhatsApp and email are described as drafts/handoffs because LocalMind cannot verify a final send.
- Tool execution has explicit states, bounded steps, typed arguments, and honest failure results.
- Reminder records are persisted before scheduling; scheduling failure removes the new record.
- Future pending reminders are restored after reboot, while overdue records are expired.
- Explicit memory content is bounded to 4,000 characters and normalized for deterministic search.
