# Tasks

## 1. Persistence model and migration

- [x] 1.1 Add versioned authoring-session, stage-attempt, and checkpoint tables
  to the managed-widget database with foreign keys, size constraints, session
  revision, action identity, stage revision, accepted-attempt pointer, and
  upstream/artifact digests; verify schema creation from an empty database.
- [x] 1.2 Add an additive migration from the current database version that
  preserves all confirmed widget definitions, revisions, runs, observations,
  and presentation caches; verify migration tests compare every pre-existing
  record before and after upgrade.
- [x] 1.3 Implement repository operations for creating, loading, observing,
  compare-and-set transitioning, finalizing, and discarding one authoring
  session; verify round-trip and stale-session tests against the real SQLite
  store.
- [x] 1.4 Persist append-only attempt outcomes and candidate/accepted checkpoint
  transitions atomically; verify injected transaction failures expose only the
  last committed session revision.
- [x] 1.5 Add startup reconciliation that marks an in-flight attempt interrupted
  without changing accepted checkpoints or retry counters; verify recreation
  tests simulate process death at each persistence boundary.

## 2. Stage graph and workflow state machine

- [x] 2.1 Define stable stage keys for feasibility, algorithm, ordered
  call-function nodes, plan, render, and assembly validation; verify dependency
  graph tests cover zero/one/multiple call functions and reject cycles or
  unknown stage keys.
- [x] 2.2 Implement durable session states and the allowed action matrix for
  start, accept/continue, retry, reprocess, cancel-attempt, and discard; verify
  table-driven tests reject every invalid transition.
- [x] 2.3 Make action IDs plus session revisions idempotent and enforce one
  non-terminal session plus one active attempt; verify duplicate UI/notification
  actions start at most one generation.
- [x] 2.4 Persist the two-repair budget per stage revision and create a fresh
  budget only for an explicit reprocess revision; verify restart, exhaustion,
  and replacement-abandonment tests.
- [x] 2.5 Implement atomic transitive invalidation when a replacement checkpoint
  is accepted, while preserving the prior graph when replacement fails; verify
  algorithm and call-function replacement tests mark exactly the dependent
  checkpoints stale.

## 3. One-attempt stage runner

- [x] 3.1 Refactor bounded context construction and deterministic validators so
  one requested stage can run from persisted accepted dependencies; verify
  existing parser/validator fixtures produce the same normalized artifacts.
- [x] 3.2 Implement a runner that performs at most one model generation for one
  action, persists success/failure/cancellation/interruption, and never advances
  automatically; verify fake-engine tests count exactly one generation per
  action.
- [x] 3.3 Build retry context from only the immediately preceding bounded
  rejected artifact, controlled failure code, checked-in contract, and accepted
  immutable dependencies; verify tests exclude raw exceptions, unrelated
  artifacts, secrets, and provider results.
- [x] 3.4 Apply device recovery before model load and preserve the 10-minute
  cancellable bound while the model remains unloaded; verify virtual-time tests
  cover readiness, cancellation, and recovery timeout without generation.
- [x] 3.5 Guarantee model unload and attempt persistence on success, validation
  failure, timeout, cancellation, and model failure; verify lifecycle tests
  assert no loaded session or active foreground attempt remains afterward.
- [x] 3.6 Replace the whole-pipeline deadline with the existing per-generation
  watchdog and durable per-action budgets in the resumable path; verify a
  workflow may span arbitrarily long user review intervals without timing out.
- [x] 3.7 Assemble and dry-run a draft only from current accepted checkpoints
  after render acceptance; verify stale or missing dependencies prevent preview
  and return a controlled local validation state.

## 4. Controller, service, and notifications

- [x] 4.1 Replace in-memory-only job ownership with a repository-backed
  application controller that reconstructs the active session after app
  restart; verify controller recreation exposes the same next allowed action.
- [x] 4.2 Scope `WidgetAuthoringService` and its foreground notification to one
  queued/deferred/running attempt and stop both at the persisted checkpoint
  boundary; verify presenter tests cover every terminal attempt outcome.
- [x] 4.3 Add idempotent Review, Continue, Retry, Reprocess, and Cancel actions
  as allowed by state, using a normal notification while waiting for the user;
  verify duplicate intents and stale session revisions are ignored safely.
- [x] 4.4 Preserve single-flight coordination with Chat/Voice model ownership and
  the shared engine; verify concurrency tests prevent a second authoring engine
  or overlapping local generations.

## 5. Review and control UI

- [x] 5.1 Replace the single progress/result surface with a repository-backed
  stage timeline that groups call functions and shows all specified durable
  states; verify Compose state tests cover fresh, resumed, interrupted, stale,
  and completed session projections.
- [x] 5.2 Add attempt details for stage/revision, attempt kind/count, duration,
  bytes, controlled validation summary, and captured artifact availability;
  verify success, rejected-artifact, and no-capture failure UI tests.
- [x] 5.3 Render captured JSON and JavaScript only as bounded escaped selectable
  plain text, with no Markdown/HTML/WebView evaluation or runtime action;
  verify hostile markup and script fixtures remain inert.
- [x] 5.4 Add Continue, Retry, Reprocess, Cancel attempt, and Discard authoring
  controls with confirmation where data would be invalidated or deleted;
  verify action enablement matches the workflow action matrix.
- [x] 5.5 Add localized English and pt-rBR copy, accessibility labels, and clear
  distinction between stage approval and final widget consent; verify resource
  parity and accessibility-focused UI tests.

## 6. Privacy, finalization, and compatibility

- [x] 6.1 Enforce checked-in payload limits and app-private no-backup storage for
  prompts, attempts, and checkpoints; verify backup-policy and oversized-payload
  tests reject leakage or unbounded persistence.
- [x] 6.2 Ensure persisted/user-visible projections exclude reasoning tokens,
  internal prompts, model paths, secrets, stack traces, raw exceptions, and
  provider results; verify redaction tests across every failure path.
- [x] 6.3 Keep final preview/consent authoritative and transactionally promote a
  confirmed draft before removing transient session data; verify failures leave
  either a recoverable session or one confirmed revision, never a duplicate or
  partial widget.
- [x] 6.4 Remove the continuous end-to-end submission path after create/edit and
  diagnostic callers use the stage workflow, retaining diagnostics that still
  require isolated probes; verify no production caller can start automatic
  multi-stage generation.
- [ ] 6.5 Reconcile this delta with `add-managed-llm-authored-widgets` after that
  dependency is archived, including its former in-memory-only privacy and
  automatic-repair requirements; verify both the canonical spec and this change
  pass strict OpenSpec validation without contradictory requirements.

## 7. Validation and delivery evidence

- [x] 7.1 Run focused repository, migration, state-machine, controller, service,
  notification, pipeline, validator, and Compose tests; verify all targeted
  tasks pass from a clean build.
- [x] 7.2 Run the full repository quality gate, OpenSpec strict validation,
  lint/static analysis, debug build, and release-candidate/R8 checks; document
  any demonstrably pre-existing failures separately.
- [ ] 7.3 On the SM-S901E, verify one successful stage, one controlled failed
  stage plus Retry, one accepted Reprocess with cascading invalidation, and
  process death between stages; record sanitized evidence that accepted work
  survives and the model is unloaded while awaiting the user.
- [ ] 7.4 Complete one E4B create workflow through preview and explicit
  confirmation, then verify the resulting widget lifecycle while no transient
  authoring session remains; record the APK/model hashes and sanitized device
  evidence.
- [x] 7.5 Update user and developer documentation for workflow states, review
  controls, persistence/privacy, recovery, and troubleshooting; verify links and
  English/pt-rBR terminology match the delivered UI.

## 8. Contract hardening from physical-device evidence

- [x] 8.1 Make fixed protocol and source identity metadata application-owned,
  while preserving parsing compatibility for accepted legacy checkpoints;
  verify new and legacy artifact fixtures.
- [x] 8.2 Normalize overlong achievable display names locally and tighten the
  feasibility prompt; verify blank names still fail and long names no longer
  consume a repair.
- [x] 8.3 Generate the plan checkpoint deterministically from accepted call
  functions, with application-owned alias/tool/version wrapping and no model
  load; verify new argument-only and legacy call functions assemble identically.
- [x] 8.4 Replace ambiguous render shorthand with explicit JSON shapes and
  classify node, field, tone, provenance, empty-result, and failure-result
  rejections; verify each controlled repair category.
- [x] 8.5 Distinguish device recovery timeout from generation timeout and apply
  selected-model memory headroom plus the established device signals before
  load; verify virtual-time and requirement-specific admission tests.
- [x] 8.6 Run targeted tests, strict OpenSpec validation, the repository quality
  gate, build the debug APK, install it with data preservation, and verify the
  installed version/update timestamp without executing the user's manual test.

## 9. Physical presentation retry follow-up

- [x] 9.1 Move exact presentation node shapes into bounded render context and
  keep the objective valid for the longest accepted alias; add a regression for
  the physical-test alias and the maximum alias length.
- [x] 9.2 Construct and validate a stage request before device admission/model
  load, and classify local construction failure as invalid schema rather than
  runtime unavailability; verify no recovery, load, or generation occurs.
- [x] 9.3 Acknowledge every explicit foreground-service refresh before acting on
  terminal state, preserving the normal notification on a rapid transition;
  verify the refresh protocol and disposition for active, review, and absent
  sessions.
- [x] 9.4 Run targeted tests, strict OpenSpec validation, the repository quality
  gate, build the debug APK, install with data preservation, and verify the
  installed version/update timestamp without executing the user's manual test.

## 10. Presentation context and repair hardening

- [x] 10.1 Replace the copied global `programApi` in render requests with a
  render-only generic contract that describes plain-object bracket access and
  binds aliases through `toolResults`; verify no domain alias or concrete
  example is embedded in the generic contract.
- [x] 10.2 Preserve controlled JavaScript-engine failure classes during render
  fixture validation and distinguish render execution failure from source
  envelope/signature rejection; verify runtime unavailability and resource
  limits are not mislabeled as invalid source.
- [x] 10.3 Apply repair anti-anchoring policy: omit rejected source for generic
  source/execution failures and require the second repair to regenerate without
  any prior artifact; verify localized first repairs may still receive bounded
  rejected content.
- [x] 10.4 Add regressions for the physical `outcomes.get()` artifact, generic
  alias context, stage-specific prompt content, and repair redaction, then run
  targeted tests and strict OpenSpec validation.
- [x] 10.5 Run the repository quality gate, build the debug APK, install with
  data preservation, and verify the installed version/update timestamp without
  executing the user's manual test.
