# Change: Report recoverable application errors

## Why

Unexpected recoverable failures currently expose raw runtime messages such as
LiteRT-LM tool-call parser errors without giving the user a safe way to share
diagnostics. Reproduction is difficult, while automatic telemetry would conflict
with ArarAI's local-first privacy boundary.

## What Changes

- Capture unexpected recoverable errors at application-owned operation
  boundaries and present one localized, non-blocking report dialog.
- Show a brief user-safe description and require an explicit `Send report`
  action before any diagnostic data leaves the device.
- Construct an allowlisted, bounded diagnostic envelope that excludes prompts,
  responses, conversation history, reasoning, raw tool output, media, file paths,
  credentials, stable device identifiers, and account information.
- Submit the envelope once through an authenticated and App-Check-protected
  Firestore REST commit. Do not enqueue, persist, retry, or schedule the envelope
  when delivery fails.
- Store accepted envelopes in a private Firestore collection protected by exact
  create-only Security Rules.
- Keep fatal process crashes outside this dialog contract because Android cannot
  guarantee UI or network completion after an uncaught fatal exception.

## Impact

- Affected spec: `local-llm-hub`
- Affected code: application error boundary, Chat presentation, diagnostic report
  domain/transport, Firestore Rules, localization, and tests
- Infrastructure: reuses the existing Spark-compatible Firebase Authentication,
  App Check, Firestore, and Security Rules architecture; no Cloud Function,
  Cloud Run service, or billable deployment boundary is introduced
- Privacy: sending remains user initiated; no conversation content or raw media
  is included; accepted reports are hosted in the United States under the same
  90-day operational retention policy
