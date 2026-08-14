## 1. Product, policy, and infrastructure decisions

- [x] 1.1 Record the report reason taxonomy, supported locales, reviewer workflow,
  report owner/contact, abuse response, incident process, and concrete retention
  and deletion periods.
- [x] 1.2 Record `ararai-report` and its default `nam5` Firestore database as the
  intended production data store, with direct create-only Android access and no
  Cloud Functions/Cloud Run endpoint or execution region.
- [x] 1.3 Update the privacy policy and draft the Google Play Data safety answers
  for report content, pseudonymous authentication, transient security metadata,
  retention, deletion, encryption, service-provider processing, and transfer to
  and hosting in the United States.
- [x] 1.4 Keep the project on Spark for production while usage fits its no-cost
  quota; document quota monitoring and service-exhaustion behavior, operational
  ownership, manual 90-day expiry, and least-privilege administrative access.

## 2. Report domain and local persistence

- [x] 2.1 Add failing tests for reportable-response eligibility and deterministic
  projection of the reported response, optional textual context, metadata, and
  media-presence flags.
- [x] 2.2 Implement bounded report draft/payload models independent of Chat UI and
  exclude raw image, audio, TTS, reasoning, hidden instructions, paths, and
  unselected history by construction.
- [x] 2.3 Implement the private bounded pending queue, stable random idempotency
  IDs, expiry/deletion behavior, attempt state, and migration-safe persistence.

## 3. In-app reporting experience

- [x] 3.1 Add `Report response` to completed assistant-message actions and a
  latest-response screen-menu action in normal Chat and Voice Chat.
- [x] 3.2 Implement the native review UI with response preview, required reason,
  bounded comment, individually reviewable context selection, disclosure, submit,
  cancel, and accessibility/localization support.
- [x] 3.3 Present draft, pending, sent, expired, and permanent-failure states and
  allow users to remove unsent queued reports.

## 4. Authenticated direct Firestore delivery

- [x] 4.1 Add Firebase Anonymous Auth and App Check/Play Integrity client setup
  with environment-specific configuration, dependency locking, verification
  metadata, no Analytics, Firestore SDK setup, and Debug Provider isolation for
  non-Play development builds.
- [x] 4.2 Implement a replaceable report transport and WorkManager delivery with
  network constraints, bounded backoff, retry classification, cancellation, and
  removal of accepted local payloads.
- [x] 4.3 Implement direct Firestore transport with anonymous-owner binding,
  deterministic owner-scoped document IDs, server timestamps, 90-day
  `expiresAt`, idempotent create-conflict verification, and precise retry/error
  classification.
- [x] 4.4 Add strict Security Rules permitting only authenticated bounded creates
  and owner-only point reads while denying list/update/delete; add Emulator Suite
  tests, least-privilege administrative access guidance, and quota monitoring.
- [ ] 4.5 Configure App Check initially with one-hour TTL, `PLAY_RECOGNIZED`
  required, `LICENSED` not yet required, and no explicit device-integrity level;
  keep enforcement disabled until SDK and debug-provider validation is complete.

## 5. Documentation and validation

- [x] 5.1 Add unit, persistence, WorkManager, Compose, transport, and Security
  Rules tests covering privacy projection, limits, retries, races, lifecycle,
  owner isolation, and Chat/Voice parity.
- [x] 5.2 Update consolidated project documentation for the ancillary reporting
  backend, privacy boundary, setup, operations, and intentionally excluded media.
- [x] 5.3 Run focused tests, the complete project quality gate, Firestore Security
  Rules tests, and strict OpenSpec validation.
- [ ] 5.4 Validate an internal Play-distributed release on a physical device for
  genuine Play Integrity attestation, successful receipt, abuse rejection,
  offline queue/retry, accessibility, and unaffected local Chat; record evidence.
- [ ] 5.5 After internal Play validation, require `LICENSED` for production while
  retaining `PLAY_RECOGNIZED`, one-hour TTL, and no explicit device-integrity
  requirement; verify both licensed acceptance and sideload rejection.
