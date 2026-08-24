# Design: Consent-based recoverable error reporting

## Context

The app already supports user-reviewed generated-content reports through direct
Firestore writes and an offline WorkManager queue. Diagnostic error reports have
different semantics: they must never be retained for later delivery and must
make exactly one user-initiated submission attempt.

Firestore's Android client has offline persistence and delivery behavior that
does not provide this contract. A direct Firestore REST commit provides an
explicit request/response boundary without using that client cache. The app
obtains Firebase Authentication and App Check tokens, sends them as request
headers, and Firestore Security Rules validate the owner-bound create.

## Decisions

### Scope only recoverable errors

Application-owned asynchronous boundaries may classify an unexpected failure as
reportable after restoring the UI to a usable state. Expected domain failures,
validation messages, cancellations, lack of connectivity, and user-denied
permissions remain normal product states and do not open the dialog.

The inference boundary represents terminal generation failures with a stable
kind (`Expected`, `Unexpected`, or `ToolCallParsing`) and an optional original
cause. Chat forwards unexpected events to the coordinator but exposes only a
localized generic message. This preserves diagnostic type/stack information
without coupling the engine to reporting or leaking the runtime message into UI.

Fatal uncaught exceptions are excluded. The app does not install a process-wide
handler that attempts network or UI work while Android is terminating it.

### Centralize one pending presentation

An application-scoped coordinator exposes at most one pending diagnostic report
to Compose. Repeated observations of the same throwable while the dialog is
visible are ignored. Dismissing or completing the dialog clears the envelope;
nothing is written to disk.

The dialog displays a localized operation-specific summary, explains the
allowlisted metadata, and offers `Send report` and `Not now`. Submission progress
prevents duplicate taps. A success or failure status is shown without silently
retrying.

### Build a bounded allowlisted envelope

The client maps a throwable to a stable category and a bounded sanitized detail.
The envelope includes only schema version, random report ID, error category,
operation stage, sanitized exception class/message/stack summary, app version,
Android API level, locale, selected model ID/runtime, configured context size,
reasoning state, enabled tool names, and report timestamp.

Sanitization removes line breaks where unnecessary, truncates every string and
collection, and rejects credential-like values, URLs with query parameters,
filesystem paths, and user content. Prompts, responses, history, reasoning text,
raw tool calls/results, images, audio, transcripts, filenames, account email,
Firebase UID, Android ID, advertising ID, hardware serial, and IP address are not
client payload fields.

### Use one direct Firestore REST attempt

The Android transport performs one Firestore REST commit with a finite timeout.
It does not use the Firestore Android write cache, WorkManager, the
generated-content pending queue, or a local database. Failure clears the
in-memory envelope after user-visible feedback.

The commit carries authenticated anonymous identity and valid App Check. Rules
validate exact keys, types, bounds, document ownership, report age, and 90-day
expiry. A create precondition prevents overwrites, and a Firestore transform
sets the server creation timestamp. Rules deny all reads, lists, updates, and
deletes from this collection.

No automatic retry is implemented in application code. The callable SDK is used
only as a single invocation boundary; the app never resubmits after completion or
failure.

### Preserve local-first behavior

The reporting coordinator and Firestore REST transport are lazy. No diagnostic
payload is constructed or transmitted before an eligible failure occurs, and no
network call starts before explicit consent. Firebase or reporting failure never
blocks local Chat recovery.

## Validation

Unit tests cover classification, sanitization, bounds, exact REST serialization,
single-attempt behavior, dismissal, duplicate suppression, and state cleanup.
Compose tests cover dialog content/actions and progress. Rules tests cover
authentication, exact schema validation, server creation time, immutable
creation, and private access. Physical validation must verify a Play-attested
submission and offline failure with no later delivery.
