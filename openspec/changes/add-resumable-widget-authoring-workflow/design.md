# Design

## Context

See `proposal.md` for motivation. The current authoring pipeline owns one
in-memory sequence from feasibility through final assembly. Its intermediate
artifacts and attempt budget are cleared when the pipeline ends or the process
dies. The application-scoped controller and foreground service keep that
sequence running away from the authoring screen, but physical validation showed
that Android can kill the process during an inter-generation model reload.

The existing security model remains mandatory: model output is untrusted,
capabilities and versions are application-derived, rejected source is never
executed, accepted source is validated with synthetic fixtures, and final
widget persistence still requires preview and explicit confirmation.

The foundational `add-managed-llm-authored-widgets` change is still active and
contains requirements that intermediate artifacts remain in memory and repairs
run inside one attempt. This change intentionally supersedes those behaviors.
Its delta targets the currently canonical `llm-widget-authoring` spec and must
be reconciled with the foundational requirements after that dependency is
archived and before this change itself can be archived.

## Goals / Non-Goals

**Goals:**

- Make every completed model attempt durable before releasing the model.
- Require an explicit user action between model-generated artifacts.
- Let users inspect bounded captured content and controlled validation results.
- Retry only the current stage and reprocess an accepted stage without silently
  overwriting its last valid checkpoint.
- Recover the workflow after process death without resetting attempt budgets or
  repeating accepted upstream work.
- Stop foreground execution and release model resources whenever the workflow
  is waiting for the user.

**Non-Goals:**

- Expose chain-of-thought, reasoning tokens, full system prompts, arbitrary
  exception text, or secrets.
- Let users edit captured JSON or JavaScript directly in the first version.
- Change tool/provider authority, sandbox policy, final consent, widget
  identity, or revision ownership.
- Support multiple concurrent authoring sessions or generation attempts.
- Preserve authoring history after the user confirms or explicitly discards the
  session; confirmed widget revisions remain in the existing repository.

## Decisions

### 1. Persist a versioned workflow state machine in the managed-widget database

Add normalized authoring-session, stage-attempt, and checkpoint records to the
existing SQLite store and migrate it without rewriting confirmed widget data.
An authoring session freezes the original instruction, target widget identity,
model artifact identity, inference configuration, protocol/schema versions, and
tool-registry digest. A checkpoint points to one accepted attempt and records an
upstream digest. Attempts are append-only and contain bounded captured content,
controlled validation outcome, timings, and sanitized metrics.

Using the existing database permits transactional state transitions and an
atomic final promotion into the existing widget/revision records. A separate
temporary database was rejected because final confirmation would require a
cross-database two-phase commit and duplicate-prevention protocol.

Every mutating action carries the session revision and a unique action ID.
Compare-and-set transitions make repeated notification taps, activity
recreation, or service redelivery idempotent.

### 2. Execute exactly one model generation per authorized attempt

The runner reconstructs bounded stage context exclusively from the frozen
session and accepted upstream checkpoints. It performs device-state admission,
loads the selected model, executes one generation under the existing per-stage
watchdog, validates/captures the result, persists the attempt transactionally,
unloads the model, and stops foreground execution.

The complete model request is constructed and checked against local size and
shape limits before device-state admission or model load. Presentation node
shapes live in the bounded render context rather than a length-sensitive stage
objective. A local request-construction failure is persisted as invalid local
schema state, never as runtime unavailability, and spends no model resources.

The current whole-pipeline deadline and automatic inter-generation reload loop
do not apply to the resumable workflow. If device state is unacceptable, the
attempt records a controlled blocked result without loading the model and waits
for the user to try again. This avoids holding a foreground service through a
cooling window. The existing recovery gate remains available to legacy and
diagnostic paths until they are removed separately.

One generation per action was chosen over automatic in-stage repairs because an
automatic repair would recreate the reload/LMK boundary this change is intended
to remove. A retry is a new user-authorized attempt that includes only the
checked-in repair instruction, controlled failure code, and bounded rejected
artifact from the preceding attempt.

Every service launch issued through `startForegroundService` carries an
explicit refresh action. The service acknowledges that action with
`startForeground` before it evaluates whether the durable attempt already
finished. If the state crossed the checkpoint boundary before delivery, the
service detaches the normal review notification and stops. This preserves the
Android foreground-service start contract even for an instantaneous local plan.

### 3. Model stages as a deterministic dependency graph

Stage keys are stable and ordered: feasibility, algorithm, one call-function
node per validated algorithm tool-call step, plan, render, and local assembly
validation. Call-function nodes are displayed as a group but checkpointed
individually. Assembly validation runs locally after render is accepted and does
not require another user action unless it fails.

Only an explicitly accepted successful attempt becomes the checkpoint used by
later stages. Reprocessing an accepted stage creates a candidate attempt while
the old checkpoint remains authoritative. If the user accepts the replacement,
the store atomically repoints that checkpoint and marks every transitive
downstream checkpoint stale. A failed replacement leaves the previous accepted
graph unchanged.

This delayed invalidation avoids destroying a working draft merely because a
replacement attempt failed. Retaining downstream checkpoints as visible but
stale gives the user an audit trail without allowing stale content into final
assembly.

The plan checkpoint remains in the graph for review, compatibility, and
dependency invalidation, but its source is derived by the application from the
accepted ordered call functions. Starting the plan stage therefore performs no
model load or generation. Call-function generations return only contract-valid
argument objects; the application-owned plan wraps those values with the
frozen alias, tool id, and contract version. Legacy accepted call functions
that return the former complete call envelope remain readable so an in-flight
session is not invalidated by an app update.

Protocol version, artifact id, function name, and input names are likewise
application-owned. New model-facing algorithm and source-fragment schemas omit
those fixed fields. Parsers accept the old redundant envelopes only for
backward-compatible session recovery and validate any legacy protocol value
before deriving the normalized artifact identity from the current stage.

### 4. Separate automatic repair limits from explicit reprocessing

The existing maximum of two repairs becomes a persisted per-stage-revision
limit. `Retry` consumes that repair budget and carries forward the immediately
preceding controlled failure. Restarting the app cannot reset it.

`Reprocess` on an accepted stage starts a new stage revision with its own repair
budget because the user explicitly authorized a replacement. Attempt history
is retained until the session is confirmed or discarded. The UI shows the
attempt count and requires a separate action for every generation, so there is
no unbounded automatic battery or model loop.

### 5. Expose artifacts and failures through a safe review projection

The UI observes repository-backed session projections rather than an
in-memory-only `StateFlow`. Each stage card shows status, attempt/revision,
duration, captured byte count, a human-readable controlled validation result,
and captured JSON or source when available. Content is length-bounded and
rendered as escaped, selectable plain text. Rejected artifacts are never sent
to the runtime, provider tools, WebView, Markdown/HTML renderer, or final
assembly.

The projection omits reasoning tokens, full internal prompts, secrets, raw
exception messages, and unbounded logs. Debug builds may continue to emit the
existing sanitized lifecycle telemetry, but telemetry is not the persistence
source of truth.

### 6. Make user intent explicit at every boundary

After a successful attempt the workflow enters `awaiting_review`; `Continue`
accepts the candidate and unlocks the next stage. After a failed attempt it
enters `awaiting_retry`; `Retry` runs a repair if budget remains. An accepted
stage exposes `Reprocess`, and every state exposes `Discard authoring` when no
attempt is running.

The persistent foreground notification exists only while an attempt is queued,
admitted, loading, generating, validating, or unloading. When an attempt ends,
it is replaced by a normal notification with `Review`, `Continue`, or `Retry`
as appropriate. Notification actions use the same idempotent controller API as
the screen.

`Continue` is workflow approval only. It does not install, enable, schedule, or
grant capabilities to a widget. Final preview and confirmation remain separate.

### 7. Reconcile process death from durable state

On startup, any attempt left in an in-flight state is marked interrupted. No
candidate checkpoint is inferred from incomplete data. The session returns to
an actionable state: retry the interrupted current stage or discard the
session. Accepted checkpoints and consumed repair counters remain intact.

At most one non-terminal session may exist. Starting a new request while one is
active opens the existing session with options to resume or discard it. Final
confirmation transactionally persists the widget revision and closes/removes
the transient session. Explicit discard removes the session, attempts, and
checkpoints after cancelling any active generation.

### 8. Normalize recoverable model variance and classify failures precisely

For an achievable feasibility result, the checked-in prompt requires a concise
display title of at most 80 characters. The parser still treats a blank title
as invalid, but deterministically collapses whitespace and truncates an
overlong non-blank title instead of spending a model retry on a cosmetic field.

The render contract lists each presentation node as an explicit JSON shape and
requires success, empty, and failed tool outcomes to be handled before reading
payload fields. Fixture validation reports controlled categories for invalid
node type, fields, tone, provenance, empty-result handling, and failed-outcome
handling. These categories contain no generated content or raw exception text
and provide targeted repair context.

Device recovery expiry is reported as `device_recovery_timed_out`, distinct
from the per-generation `timed_out` watchdog. Recovery admission combines the
existing thermal and pressure signals with a minimum available-memory estimate
derived from the selected model artifact, so a cool device with inadequate
headroom does not start a likely-to-be-killed load. Android thermal status
remains authoritative; the battery-temperature ceiling is a conservative
fallback rather than a proxy for a reported thermal event.

### 9. Keep render contracts generic and make repairs diagnostic-driven

The render stage receives a stage-specific API contract instead of the global
`programApi`. The contract describes `outcomes` as a plain JavaScript object,
requires bracket access using an alias copied from `toolResults`, and explicitly
forbids Map-style `get()`. Concrete aliases remain execution data in
`toolResults`; examples and contract prose do not embed a domain-specific or
real alias that a model could copy into another workflow.

Render fixture execution preserves the sandbox's controlled failure class.
Static source envelope or signature rejection remains `invalid_source`, while a
script failure during render validation is `invalid_render_execution`.
Resource-limit and runtime-unavailable results remain their own categories.
No raw exception text or generated source enters a diagnostic category.

Repair prompts are chosen from the failure class and attempt number. A precise,
localized first repair may receive the immediately preceding bounded artifact.
Source-envelope and render-execution repairs omit it and regenerate from the
authoritative contract. The second repair for every category also omits prior
source, preventing repeated invalid output from anchoring the final attempt.

## Risks / Trade-offs

- **More user interactions and cold model loads** → Keep stage summaries clear,
  provide actions in notifications, group call-function progress, and avoid a
  separate click for local assembly validation.
- **Schema migration can endanger confirmed widgets** → Use additive tables,
  migration tests from the current database version, foreign keys, and a
  rollback-safe backup/restore test before physical validation.
- **Persisted rejected output may contain sensitive prompt-derived text** →
  Bound every payload, keep it in app-private storage, never render it as rich
  content, and delete the complete transient session on confirmation/discard.
- **Upstream replacement can leave inconsistent descendants** → Compute
  dependency digests transactionally and exclude stale checkpoints from context
  construction and assembly.
- **Duplicate actions can spend extra generations** → Require action IDs plus
  session revisions and enforce single-flight compare-and-set transitions.
- **A single generation can still be killed by LMK** → Persist the running
  transition before model load and convert it to an interrupted retryable
  attempt on the next startup; previously accepted stages remain safe.
- **A fast local stage can finish before its foreground-service start is
  delivered** → Acknowledge every explicit foreground refresh first, then keep,
  detach, or remove the notification from the latest durable state.
- **Removing rejected source can discard otherwise-correct details** → Preserve
  it only for the first repair of precise localized validation categories; use
  immutable accepted dependencies and the checked-in contract for clean
  regeneration after source/execution failures or a repeated rejection.

## Migration Plan

1. Add the authoring-session tables and migration while leaving the current
   continuous pipeline as the temporary execution path.
2. Add repository/state-machine tests, including migration, process death,
   idempotent actions, repair budgets, and cascading invalidation.
3. Introduce the one-attempt stage runner behind the authoring controller and
   verify model unload plus foreground-service shutdown on every outcome.
4. Replace the authoring screen and notification projection with the durable
   workflow UI, then remove the continuous full-pipeline submission path.
5. Validate process-death recovery and at least one complete E4B workflow on the
   SM-S901E before enabling production authoring eligibility.

Rollback keeps confirmed widgets intact. Incomplete resumable sessions may be
discarded by the previous app version because no confirmed widget revision is
created before final confirmation.
