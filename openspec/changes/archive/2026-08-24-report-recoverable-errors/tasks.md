## 1. Specification and domain

- [x] 1.1 Add the active OpenSpec delta and validate it strictly.
- [x] 1.2 Add bounded diagnostic report models, classification, sanitization, and
  deterministic unit tests.

## 2. One-shot transport and Firestore boundary

- [x] 2.1 Add a replaceable one-shot Firestore REST transport with authentication,
  App Check, finite timeout, and no local persistence or application retry.
- [x] 2.2 Add exact create-only Firestore Rules with owner binding, server creation
  time, retention validation, and tests.
- [x] 2.3 Deny reads, lists, updates, deletes, and overwrites of diagnostic reports.

## 3. Recoverable error experience

- [x] 3.1 Add an in-memory application coordinator with duplicate suppression and
  explicit clear-on-dismiss/success/failure behavior.
- [x] 3.2 Integrate the boundary with unexpected Chat generation failures without
  reclassifying expected cancellations or domain errors.
- [x] 3.2.1 Preserve typed terminal generation failures, route them through the
  diagnostic coordinator, and keep technical messages out of Chat UI.
- [x] 3.3 Add localized Compose dialog, one-shot submission feedback, accessibility,
  and UI tests.

## 4. Documentation and validation

- [x] 4.1 Update privacy, Firebase setup, and project documentation for the new
  consent-based diagnostic flow and Spark-compatible Firestore boundary.
- [x] 4.2 Run targeted tests, Rules tests, the complete quality
  gate, and strict OpenSpec validation.
- [x] 4.3 Physical Play/App Check submission and offline/no-later-delivery
  validation not executed; this remains device/environment evidence outside the
  automated quality gate.
