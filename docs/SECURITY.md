# Security

## Principles

- Treat model output and imported content as untrusted input.
- Keep Android capabilities behind deterministic typed actions.
- Request the minimum runtime permission at the point of use.
- Require confirmation before writes or communication handoffs.
- Never claim success without the strongest verification Android exposes.
- Never place credentials or personal content in logs or activity traces.

## Current Controls

- Fixed package allowlist for application launches.
- Schema and risk validation in the structured tool registry.
- Calendar provider-ID verification after event creation.
- Exact model byte-size and SHA-256 verification before activation.
- App-specific model and inbox storage.
- No arbitrary shell execution, Accessibility Service, private database access, or hidden UI automation.
- No embedded OAuth client secrets, API keys, or cloud inference credentials.

## Reporting A Vulnerability

Do not open a public issue containing exploit details, credentials, or private user data. Contact the repository owner privately until a dedicated security contact is published.

## Future Integrations

Authenticated integrations must document scopes, consent, token storage, revocation, retention, failure behavior, and verification before they are merged.
