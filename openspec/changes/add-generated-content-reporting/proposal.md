# Change: Add generated-content reporting

## Why

Google Play's generative-AI policy requires applications that generate content
to provide an in-app mechanism for users to report or flag offensive output
without leaving the application. ArarAI currently generates responses locally
but has no integrated reporting path. A compliant path must be discoverable in
both Chat modes, preserve the app's local-first privacy posture, and avoid
turning private conversations and media into an implicit telemetry stream.

## What Changes

- Add `Report response` actions to completed assistant responses in normal Chat
  and Voice Chat, with an additional discoverable action for the latest eligible
  response in each screen's menu.
- Present an in-app review flow where the reported response is required and the
  user explicitly selects any preceding textual context to disclose.
- Collect a bounded reason, optional comment, minimal technical metadata, and
  presence-only media indicators; do not upload raw images, audio, generated
  speech, full conversations, model files, or device identifiers.
- Submit reports directly from the Android Firebase SDK to a write-only
  Firestore collection, protected by Firebase anonymous authentication, App
  Check with Play Integrity enforcement, and strict Security Rules that permit
  only bounded document creation by the authenticated owner.
- Queue bounded pending reports locally when delivery fails and retry them with
  stable idempotency identifiers, while keeping user-visible status and deletion
  controls.
- Add the required privacy disclosure, retention/operational documentation,
  Firebase configuration, United States data-hosting disclosure, Google Play
  Data safety review, and release checks.

## Impact

- Affected spec: `local-llm-hub`
- Affected code: Chat and Voice Chat message actions, report review UI, local
  report queue, WorkManager delivery, Firebase Auth/App Check client setup, and
  tests
- New managed infrastructure: one private, non-listable report collection in
  the configured `nam5` United States multi-region, used within the Firestore
  no-cost quota on Firebase Spark; no Cloud Functions or Cloud Run endpoint is
  required
- Rollout: validate Security Rules with the Emulator Suite, enable Firestore App
  Check enforcement, then validate direct creation from a Play-distributed build
- Privacy: only user-reviewed report content leaves the device; core inference,
  conversations, and media remain local; submitted report data is hosted in the
  United States
- Policy: adds the in-app generated-content reporting path and corresponding
  Data safety/privacy disclosures required before production release
