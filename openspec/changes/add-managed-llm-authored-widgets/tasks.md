## 1. Establish prerequisites and regression baselines

- [x] 1.1 Verify `add-sandboxed-javascript-widget-runtime` is implemented and
  passes every automated gate, capture its supported program/API versions and
  resource policies, and provide its optimized manual physical validator.
  Physical arm64 acceptance may remain pending during implementation, but keep
  production widget activation, release acceptance, final validation, and both
  changes' archival blocked until that report passes; do not start when the
  dependency is otherwise incomplete or incompatible.
- [x] 1.2 Add regression tests for current Home destinations, model capability
  parsing, normal Chat/Voice tool advertisement, Wikipedia name/schema/results,
  enablement, host validation, failures, and source capture; verify the baseline
  passes before adding widget behavior.
- [x] 1.3 Define the managed-widget privacy/threat matrix and checked-in limits
  for schedules, revisions, cached output, observations, run retention, source,
  proposal context, and UI trees; verify each limit has a corresponding test or
  documented device check.

## 2. Add revisioned widget persistence

- [x] 2.1 Add failing repository tests for empty state, atomic first creation,
  immutable revisions, active revision replacement, consent digest, unsupported
  schemas, corruption, and rollback on transaction failure.
- [x] 2.2 Implement the separate versioned SQLite widget database and
  dispatcher-isolated repository for definitions, program revisions,
  presentation cache, runs, and typed observations; verify foreign keys and
  migrations preserve transaction invariants.
- [x] 2.3 Implement bounded retention and widget-owned history projection;
  verify age/count eviction, nearest-age/tolerance lookup, missing values, type
  validation, and cross-widget access rejection.
- [x] 2.4 Implement confirmed edit activation and complete deletion transactions;
  verify failed/unconfirmed edits preserve the previous revision and deletion
  removes every owned row while rejecting late writes.

## 3. Add structured Wikipedia data for widgets

- [x] 3.1 Define typed `wikipedia_pages@1` request/result contracts and add
  failing registry/gateway tests for strict query/language input, bounded page
  fields, explicit `WIDGET` eligibility, enablement, readiness, versions,
  and controlled failures.
- [x] 3.2 Refactor or extend the existing Wikipedia parser/transport behind the
  new typed widget executor without reconstructing extracts from narrative
  context; verify malformed UTF-8/JSON, size/result limits, redirect rejection,
  host allowlisting, cancellation, timeout, no-results, and canonical links with
  deterministic local responses.
- [x] 3.3 Register `wikipedia_pages` in application composition while preserving
  `wikipedia_search@1`; verify normal Chat and Voice Chat names, schemas, call
  limits, result framing, source capture, and model compatibility before the
  later explicit dual-consumer expansion.
- [x] 3.4 Verify credentials, headers, endpoints, timeout/provider selection,
  raw responses, and arbitrary Wikipedia text cannot enter widget arguments,
  contracts, logs, or executable paths.
- [x] 3.5 Add the typed `wikipedia_on_this_day@1` request/result contract and
  fixed official REST transport; test valid dates/languages, bounded events,
  malformed/oversized responses, canonical links, timeouts, cancellation, and
  rejection of request-controlled transport.
- [x] 3.6 Make `wikipedia_pages@1` and `wikipedia_on_this_day@1` explicitly
  eligible for both `MODEL` and `WIDGET`, retain `wikipedia_search@1` as a
  compatibility binding, and declare both structured tools for checked-in Chat
  models.
- [x] 3.7 Add clear model tool descriptions, turn instructions, source capture,
  and one combined per-turn Wikipedia call budget; verify Chat and Voice choose
  on-this-day for dated historical events and pages for direct stable lookup.
- [x] 3.8 Replace the flawed date-page authoring example and PT/EN fixtures with
  `wikipedia_on_this_day@1`, seeded selection from actual event items, and
  result-proven links.

## 4. Coordinate bounded widget runs

- [x] 4.1 Implement the managed execution coordinator over the active revision,
  effective grant, runtime, gateway, owned observations, and transactional
  cache/run sink; verify one successful fake run records the expected revision,
  outcome, presentation, timestamps, and optional observations.
- [x] 4.2 Implement the repository-backed per-widget execution lease and final
  revision/consent/deletion check; verify manual/scheduled races coalesce and
  late results after edit, disable, or deletion are discarded.
- [x] 4.3 Implement controlled failure and stale-cache behavior; verify invalid
  program, denied capability, unavailable tool, malformed result/presentation,
  timeout, cancellation, and runtime failure retain the last valid presentation
  with sanitized latest-attempt state.
- [x] 4.4 Add bounded sanitized run logging distinct from observations; verify
  prompts, source, provider content, credentials, headers, raw protocol, stack
  traces, and arbitrary exception text are absent.

## 5. Add scheduling and application lifecycle composition

- [x] 5.1 Implement the widget scheduler with deterministic unique work names,
  supported intervals of at least one hour, network constraints derived from
  confirmed tools, and replace/cancel operations; verify create, schedule edit,
  enable, disable, and delete leave exactly the intended WorkManager state.
- [x] 5.2 Implement the widget worker using narrow application-scoped runtime,
  repository, and gateway composition without a model engine; verify process
  recreation, disabled/incompatible definitions, cancellation, and controlled
  completion with WorkManager test drivers.
- [x] 5.3 Connect explicit manual refresh to the same lease/coordinator and
  enforce no execution while disabled; verify a manual/periodic race creates at
  most one tool call and no immediate automatic retry.
- [x] 5.4 Add startup reconciliation for stored enabled definitions and unique
  work without executing widgets on the main thread; verify repeated startup is
  idempotent and does not load a model or duplicate work.

## 6. Establish and characterize the initial one-shot authorship foundation

- [x] 6.1 Extend static model capability parsing/configuration for explicit
  `propose_widget` verification and add tests for eligible, ineligible, missing,
  and malformed declarations without changing normal model tool resolution.
- [x] 6.2 Define the bounded proposal schema and add failing tests for one valid
  call, no call, multiple calls, malformed/oversized fields, invented identity,
  digest, limit, tool/version, capability, endpoint, and unsafe presentation.
- [x] 6.3 Implement the ephemeral authoring conversation and capture-only
  `propose_widget` adapter; verify it advertises no normal application tools,
  executes no provider or JavaScript, persists no Chat session, accepts at most
  one proposal, and releases on success, failure, cancellation, or navigation.
- [x] 6.4 Build bounded creation/edit context from non-secret widget API and
  tool descriptors plus only the relevant current definition/source; verify
  credentials, unrelated widgets, observations, run logs, prompts, and Chat
  history are absent.

## 7. Validate drafts, permissions, and consent

- [x] 7.1 Implement the draft builder that replaces model-owned control fields,
  computes canonical program/consent digests and effective grants, validates the
  package, and runs only side-effect-free planning; verify invalid drafts cause
  no database, WorkManager, tool, or provider mutation.
- [x] 7.2 Implement normalized draft summaries and semantic/source diffs covering
  schedule, tools, capabilities, retention, actions, and presentation scope;
  verify every authority expansion is identified and an edit retains the
  application-owned widget identity.
- [x] 7.3 Implement explicit create-and-enable, save-disabled if offered, edit,
  decline, and blocked-tool consent transitions; verify only confirmation
  atomically stores/activates a revision and no flow silently enables a shared
  tool or provider.
- [x] 7.4 Add authoring-controller tests for retry after invalid output,
  user cancellation, missing/ineligible model, model replacement, and failed
  confirmation; verify existing managed widgets remain operable throughout.

## 8. Render and manage widgets in Compose

- [x] 8.1 Implement the fixed accessible Compose renderer for the supported
  presentation tree and semantic tokens; verify node/depth/text limits,
  unsupported nodes, unsafe links/actions, and rendering failures cannot create
  code, WebViews, callbacks, or arbitrary intents.
- [x] 8.2 Add focused Widgets list/empty-state and Home navigation files; verify
  status, last result time, stale/error state, enablement, and create action are
  represented without moving complete screens into `ArarAiApp.kt`.
- [x] 8.3 Add widget detail, permissions/revision inspection, refresh,
  enable/disable, duplicate, deletion confirmation, and bounded run-history UI;
  verify state restoration and late controller events cannot act on a deleted or
  different widget.
- [x] 8.4 Add creation/edit prompt, generation progress, validation errors,
  source/behavior preview, permission/schedule summary, diff, blocked-tool path,
  and confirmation UI; verify navigation away cancels authorship and leaves no
  unconfirmed durable state.
- [x] 8.5 Route validated canonical Wikipedia link actions through the existing
  application-owned URI opener; verify only user gestures can open the expected
  HTTPS article host/path and invalid actions are inert.
- [x] 8.6 Add a user-initiated local tool-calling diagnostic matrix for the
  selected authoring model with copy/share JSON; verify it isolates minimal,
  flat, full-schema/short-payload, production-context/flat-schema, and complete
  production cases, aborts safely after a callback timeout, and excludes
  prompts, generated arguments, source, raw output, arbitrary exceptions, and
  stack traces.

## 9. Complete the Wikipedia vertical slice

- [x] 9.1 Add deterministic model fixtures for Portuguese and English prompts
  that create and edit a Wikipedia widget using current local day/month,
  `wikipedia_on_this_day@1`, supplied seeded event selection, and the supported card tree;
  verify proposal validation and summaries/diffs cover success and impossible
  requests.
- [x] 9.2 Add an end-to-end local-fake test from prompt proposal through consent,
  persistence, unique work, tool dispatch, presentation cache, list/detail
  rendering state, refresh failure/staleness, edit, disable, and deletion.
- [ ] 9.3 Preserve the physical one-shot E2B/E4B matrix and real E4B
  `InvalidToolArguments`/`InvalidPlan` evidence, then validate the replacement
  multi-round protocol on a supported arm64 device in Portuguese and English,
  including valid creation/edit, `unachievable`, clarification, malformed stage,
  bounded repair success/exhaustion, unavailable tool, cancellation, and
  arbitrary-endpoint refusal. Record model/build/prompt/stage/outcome evidence
  and advertise the new protocol only for models that pass it.
- [ ] 9.4 Validate a confirmed Wikipedia widget through manual and background
  refresh, process recreation, network loss/recovery, stale cache, safe article
  opening, schedule replacement, and deletion on a supported arm64 device;
  record that WorkManager timing is inexact and that no model loads in workers.

## 10. Complete first-pass documentation and automated validation

- [x] 10.1 Update README, project context, architecture, privacy/data-safety,
  user guidance, and device-validation documentation for internal widgets,
  JavaScript source storage, consent, scheduling, Wikipedia network use,
  observations/logs, deletion, and explicit non-goals; verify docs distinguish
  automated evidence from physical checks.
- [x] 10.2 Run focused repository, scheduler/worker, runtime, tool, authoring,
  model-adapter, Compose, Chat, and Voice Chat tests plus instrumentation builds;
  verify all pass with no live network dependency in automated suites.
- [x] 10.3 Run `openspec validate --all --strict`, dependency/license checks,
  lint, optimized release-candidate build, `git diff --check`, and
  `scripts/quality-gate.sh`; verify each completes successfully.
- [x] 10.4 Review the completed change against both dependency/runtime contracts
  and the then-current four delta specs, record physical-device evidence and
  unexecuted exclusions explicitly, and verify no currency provider, launcher
  widget, arbitrary endpoint, remote sync, sharing, notification, or unattended
  model behavior entered the implementation.

## 11. Replace one-shot authorship with the bounded multi-round protocol

- [x] 11.1 Define versioned in-memory types and strict schemas for authoring
  state, feasibility/capability selection, typed algorithm steps, generated
  fragments, assembly, controlled stage failures, progress, and repair budget;
  replace the historical `propose_widget` model marker with explicit
  `widget_authoring_pipeline_v1` catalog parsing and eligibility;
  verify malformed/additional/oversized fields, duplicate/cyclic steps,
  invented tools, authority expansion, and live-result-dependent calls fail
  before JavaScript generation.
- [x] 11.2 Add the deterministic widget runtime standard library over a fresh
  immutable Kotlin snapshot for every manual/background/preview/dry-run
  execution; expose granted current local date/time, timezone, language, and
  seed ergonomically while keeping ambient `Date.now()`, host clocks, unseeded
  randomness, Android/JVM objects, and callbacks unavailable. Verify same-input
  replay and later-run clock refresh.
- [x] 11.3 Implement isolated feasibility and typed-algorithm model stages with
  one capture-only tool per fresh conversation and application validation
  between them; verify `achievable`, `unachievable`, and
  `needs_clarification`, relevant creation/edit context, registry enforcement,
  and no provider/JavaScript/persistence side effects.
- [x] 11.4 Generate one bounded named JavaScript call function per validated IR
  tool step using only its fixed tool/version, deterministic runtime stdlib, and
  declared inputs; execute normal/boundary synthetic fixtures and validate
  exact call output/arguments before freezing each fragment. Verify up to four
  calls, invalid syntax, undeclared input, wrong type/range, invented transport,
  resource limit, and late callback handling.
- [x] 11.5 Generate and validate a short `plan` orchestrator from immutable call
  signatures and a separate `render` function from typed synthetic outcomes;
  verify success/failure/empty presentation paths, provenance, capabilities,
  limits, and that neither stage can rewrite accepted call fragments.
- [x] 11.6 Implement fixed-order byte-preserving source assembly, manifest and
  digest construction, and a full exact-package dry-run with synthetic runtime
  and tool outcomes; verify the previewed source is the validated assembly and
  no production provider, durable state, schedule, or consent changes occur.
- [x] 11.7 Add at most two targeted repair generations across one attempt,
  scoped to the rejected stage, frozen dependencies/envelope, relevant
  contract, and controlled error code; enforce per-stage and total generation,
  context/output, time, and artifact budgets. Verify successful repair,
  exhaustion, timeout, cancellation, attempted authority expansion, and absence
  of secrets/provider data/arbitrary exceptions in repair prompts.
- [x] 11.8 Replace the one-shot controller with an application-owned authoring
  state machine that may reuse only the loaded model workload while creating a
  fresh conversation per stage; verify state transitions, lifecycle release,
  model replacement/unload, navigation cancellation, cleared in-memory
  artifacts, discarded late callbacks, and unchanged normal Chat/Voice paths.
- [ ] 11.9 Update the Compose authoring UI to show feasibility, tool selection,
  algorithm, call N/M, orchestration, presentation, validation, repair count,
  failure, and draft-ready stages; verify cancellation at every stage, state
  restoration without resuming hidden model work, accessibility, and no
  intermediate artifact is presented as saved or executable.
- [x] 11.10 Replace the one-shot-only diagnostic with sanitized per-stage schema
  characterization plus a complete synthetic pipeline run; retain historical
  suite-v2 evidence; report controlled complete-pipeline failure stage/code and
  per-tool callback count/byte aggregates; localize feasibility failures to
  controlled JSON/root, field-set, protocol, display-name, enabled, schedule,
  tool, runtime, presentation, or outcome categories shared with bounded repair;
  report the controlled failure sequence per attempt and emit the same sanitized
  metadata to a debug-only Logcat tag; and verify both paths exclude prompts,
  rejected values, algorithms, generated fragments/source, arguments, provider
  content, arbitrary exceptions, secrets, and stack traces.
- [x] 11.11 Make the feasibility prompt self-contained about conditional
  outcome fields, distinguish initial capability selection from later frozen
  authority, translate controlled repair codes into actionable checked-in
  guidance, and use an unambiguous current-date historical-event prompt for the
  complete physical diagnostic; verify the prompt and repair behavior without
  exposing generated artifacts in diagnostics.
- [x] 11.12 Replace the ambiguous random-event diagnostic request with one
  explicit deterministic current-date widget contract, express feasibility as
  a choose-first outcome decision table, prioritize the supported achievable
  branch in outcome repair, and version the physical diagnostic as suite v8;
  verify exact prompt, repair, and sanitized report behavior.
- [x] 11.13 Move operation and controlled-variation semantics from the user
  instruction into bounded application context: declare create/edit mode,
  accept behavior-only instructions, map natural random/varied selection to
  the `seed` grant and `runtime.seededIndex(length)`, keep `Math.random`
  unavailable, restore a natural Portuguese physical prompt, and version the
  changed diagnostic as suite v9; verify context, prompt, and report version.
- [x] 11.14 Add independent suite-v10 feasibility probes for full-context plus
  natural prompt, compact-context plus natural prompt, and compact-context plus
  explicit control prompt; run exactly one generation per selected probe and
  report only sanitized input sizes/context hash plus first-event, capture,
  terminal, watchdog, return, and cleanup-overrun timings. Expose each probe
  separately in the authoring UI so physical A/B runs can start in distinct
  cold application processes without first heating the device with the schema
  matrix.
- [x] 11.15 Promote the physically characterized compact feasibility context to
  the production pipeline while retaining the full context for later stages;
  add a suite-v11 cold complete-pipeline diagnostic that runs no schema matrix
  or feasibility probe first, and verify its request sequence and sanitized
  reporting independently.
- [x] 11.16 Add an explicitly confirmed, debug-build-only raw diagnostic
  sidecar that preserves exact stage instructions, context, schemas, and tool
  argument bytes in a replace-on-export cache file; keep release builds and the
  normal report/log path raw-free, and verify opt-in capture plus default
  absence and sanitized-report non-contamination.
- [x] 11.17 Use the suite-v11 raw baseline to replace the model-owned ten-field
  feasibility envelope with a suite-v12 four-field decision (`outcome`,
  `message`, `periodicIntervalHours`, and `toolIds`); derive protocol metadata,
  enablement, registered versions, runtime/presentation grants, and terminal
  normalization in the application; verify strict parsing, controlled repair,
  terminal normalization, exact raw opt-in, and sanitized default reporting.
- [x] 11.18 Add a suite-v13 isolated production-like algorithm probe backed by
  an application-normalized achievable feasibility fixture, and record one
  sanitized lifecycle entry for every complete-pipeline stage attempt so native
  stalls, missing callbacks, transport/parser failures, invalid artifacts, and
  cleanup overruns can be distinguished without exposing model or user content.
- [x] 11.19 Use the suite-v13 raw E4B evidence to normalize `toolId` and
  `contractVersion` away from non-tool algorithm steps while retaining strict
  unknown-field, tool-call identity/version, frozen-envelope, dependency, and
  resource validation; add regression coverage and rerun the relevant gates.
- [x] 11.20 Remove model-authored per-step `runtimeInputs` from the algorithm
  schema and normalized artifact; derive runtime authority only from the frozen
  feasibility envelope, discard legacy wire values such as `month` and `day`,
  allow non-tool steps to omit tool metadata, and retain strict validation for
  actual tool calls and unknown fields.
- [x] 11.21 Remove model-authored `contractVersion` from algorithm capture,
  resolve each selected tool ID to the exact version frozen by feasibility,
  discard legacy wire versions, retain strict invented-tool/unknown-field
  rejection, and cover the physical missing-version artifact with regression
  tests.
- [x] 11.22 Add debug-only sanitized LiteRT-LM lifecycle telemetry for engine
  initialization/reuse, conversation create/reuse, submission, first/terminal
  callback, cancel, close, and cleanup; correlate process-local request/resource
  IDs, detect concurrent generations, and sample threads, RSS, swap, and thermal
  status without recording model/user/generated content.
- [x] 11.23 Run the focused engine/authoring gates and one physical cold pipeline
  to verify the new runtime telemetry after the explicitly requested no-test
  implementation pause.
- [x] 11.24 Replace the strong process-lifetime discarded-conversation registry
  with weak identity tracking, add PSS/native-PSS/native-heap/Java-heap/system
  memory checkpoints around native lifecycle operations, cover identity
  semantics locally, and run focused/full local gates plus isolated and complete
  physical validation on the SM-S901E. Record the remaining algorithm timeouts
  and Android low-memory termination without enabling production widgets.
- [x] 11.25 Add an atomic LiteRT-LM model reload after an achievable feasibility
  decision and before algorithm generation, preserve the fixed authoring
  inference policy, expose bounded reload progress/telemetry, avoid reloads for
  terminal feasibility outcomes, and cover the transition with local runtime
  and pipeline regressions.
- [x] 11.26 Validate the feasibility-to-algorithm model reload on the SM-S901E,
  comparing reload, algorithm callback, conversation cleanup, memory, swap, and
  thermal evidence with task 11.24 before changing any production eligibility.
  Record the successful atomic reload and the remaining isolated algorithm
  no-callback timeout as negative eligibility evidence.
- [x] 11.27 Replace the single algorithm-boundary reload with a serialized,
  cancelable unload/recovery/reload barrier before every subsequent generation,
  including repairs; require bounded stable Android thermal, battery, process
  PSS, available-memory, and low-memory signals, expose progress and sanitized
  telemetry, add local regressions and suite-v15 diagnostics, then validate the
  isolated algorithm probe and complete pipeline on the SM-S901E.

## 12. Revalidate the revised change and release gate

- [ ] 12.1 Replace one-shot model fixtures with PT/EN multi-stage creation/edit
  fixtures and add complete local-fake acceptance from feasibility through
  exact assembly, repair, consent, persistence, manual/background execution,
  rendering, and deletion; cover impossible operations and unsupported adaptive
  algorithms without live network access.
- [x] 12.2 Update README, project context, architecture, privacy/data-safety,
  user guidance, stage/error copy, diagnostics, and device-validation history
  for the multi-round pipeline, deterministic stdlib, bounded repair, latency,
  cancellation, and explicit non-goals.
- [x] 12.3 Run focused authoring/runtime/tool/repository/scheduler/worker/UI and
  Chat/Voice regressions, Firestore emulator tests, QuickJS sanitizer/release
  tests, Spotless, Detekt, Android lint, debug/instrumentation builds, optimized
  R8 release candidate, dependency/license/artifact checks,
  `openspec validate --all --strict`, and `git diff --check`.
- [ ] 12.4 Complete tasks 9.3 and 9.4 on a supported arm64 device, record the
  actual stage timings and total authoring latency, and keep production widget
  activation and model protocol eligibility disabled for every unverified model
  or failed acceptance path.
- [ ] 12.5 Review the final implementation against the sandbox dependency and all
  five delta specs; verify dynamic code remains model-generated, accepted
  fragments are assembled without rewriting, confirmed refreshes never load a
  model, and deferred endpoints/providers/plugins/launcher widgets/sharing/
  notifications remain absent before accepting or archiving either change.
