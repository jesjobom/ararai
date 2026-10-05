# Spec Delta

## MODIFIED Requirements

### Requirement: Application-scoped authoring jobs

The application SHALL run each user-authorized widget-authoring stage attempt
as an application-scoped job that continues while the user navigates within the
app and survives leaving the authoring screen while the process lives. The job
SHALL use the shared application-scoped local LLM engine, SHALL NOT create a
second concurrent engine instance, and SHALL end after persisting the attempt
outcome and unloading the model.

Previously accepted checkpoints SHALL remain durable when an active attempt is
cancelled, interrupted, or killed with the process. The application SHALL NOT
infer a successful artifact from an attempt that did not transactionally reach
its completed state.

#### Scenario: Generation continues after leaving the screen

- **WHEN** the user starts one stage attempt and navigates away from the
  authoring screen without cancelling
- **THEN** that attempt keeps running at application scope
- **AND** returning to the authoring screen shows the same durable session and
  attempt state.

#### Scenario: Process death is reported, not silently ignored

- **WHEN** the process dies while a later stage attempt is in flight
- **THEN** the in-flight attempt is reported as interrupted after restart
- **AND** every previously accepted checkpoint and consumed retry count remains
  available
- **AND** the user may retry the interrupted stage without repeating accepted
  upstream stages.

### Requirement: Single-flight authoring

The application SHALL allow at most one non-terminal widget-authoring session
and at most one queued, deferred, or running stage attempt at a time. Starting
a new authoring request while a session already exists SHALL open the existing
session with resume or discard controls instead of creating a competing
session. A duplicate action for the same session revision SHALL be idempotent.

#### Scenario: New request finds an existing session

- **WHEN** the user requests another widget generation while an existing
  authoring session is awaiting review, retry, or an active attempt
- **THEN** the new request does not create another session or model generation
- **AND** the application opens the existing session with its current
  actionable state.

#### Scenario: Second request refused while a job is active

- **WHEN** the user requests another widget generation while a stage attempt is
  queued, deferred, or running
- **THEN** the request is refused with an explanation that authoring is already
  in progress
- **AND** the active session and attempt are unaffected.

#### Scenario: Duplicate notification action is idempotent

- **WHEN** the same notification action is delivered more than once for one
  session revision
- **THEN** at most one state transition and one model generation occurs.

### Requirement: Cancelable background authoring

The user SHALL be able to cancel the active stage attempt from the authoring UI
or its foreground notification. Cancelling SHALL stop active generation,
release model resources, persist the attempt as cancelled, and retain
previously accepted checkpoints. When no attempt is active, the user SHALL be
able to discard the complete authoring session, which removes its transient
attempts and checkpoints without changing a confirmed widget.

#### Scenario: Cancel from the notification

- **WHEN** the user taps cancel while a stage attempt is running
- **THEN** active model work stops and the attempt becomes cancelled
- **AND** accepted upstream checkpoints remain available
- **AND** the session returns to an actionable retry or reprocess state.

#### Scenario: Discard an inactive session

- **WHEN** the user explicitly discards an authoring session while no attempt
  is running
- **THEN** its checkpoints, candidates, and attempt history are deleted
- **AND** no existing confirmed widget or revision is changed.

### Requirement: Device-state deferral

The application SHALL evaluate device state using the established thermal,
battery-temperature, process-memory, available-memory, low-memory, and
consecutive-sample criteria before loading the model for every stage attempt.
The available-memory criterion SHALL include a bounded requirement derived
from the selected model artifact in addition to the system-wide free-memory
fraction. A recovery-window expiry SHALL be reported separately from a model
generation watchdog expiry.
If state is unacceptable, the user-authorized attempt SHALL remain cancellable
while the model stays unloaded and SHALL wait at most 10 minutes for a stable
window. Failure to stabilize SHALL persist a controlled retryable outcome and
SHALL NOT advance the workflow.

After every generation outcome, the application SHALL persist the attempt,
unload the model, and end foreground execution. It SHALL NOT automatically
reload the model or start another generation until a subsequent explicit user
action.

#### Scenario: Wait with the model unloaded

- **WHEN** the user starts a stage attempt while the recovery criteria are not
  stable
- **THEN** the attempt waits with a visible reason while the model remains
  unloaded
- **AND** the user may cancel the wait
- **AND** generation begins only after the required stable samples are
  observed.

#### Scenario: Defer under severe thermal status

- **WHEN** a stage attempt starts while thermal status is severe or worse
- **THEN** it remains deferred with the reason visible while the model stays
  unloaded
- **AND** no generation runs while the unacceptable state persists.

#### Scenario: Exhausted retry budget fails explicitly

- **WHEN** the bounded recovery wait ends without observing an acceptable
  device state
- **THEN** the attempt persists `device_recovery_timed_out`, not the model
  generation `timed_out` failure
- **AND** the UI and notification expose Retry without advancing the workflow.

#### Scenario: Inter-generation recovery needs more than three minutes

- **WHEN** a user-authorized stage attempt has not observed an acceptable
  stable window after three minutes
- **THEN** it continues waiting instead of failing
- **AND** it waits for at most 10 minutes before persisting a retryable recovery
  timeout.

#### Scenario: Completed stage does not trigger another generation

- **WHEN** one model generation succeeds, fails validation, times out, or is
  cancelled
- **THEN** the application persists that outcome and unloads the model
- **AND** no recovery reload or later stage begins until the user explicitly
  requests it.

### Requirement: Visible authoring job state

The application SHALL expose the durable authoring session as a stage timeline
showing pending, queued, deferred, running, awaiting-review, awaiting-retry,
accepted, stale, interrupted, cancelled, and completed states as applicable.
While one attempt is active, a persistent foreground-service notification SHALL
match its state and offer cancellation. When the attempt ends, foreground
execution SHALL stop and a normal notification SHALL expose the next safe
review, continue, or retry action.

#### Scenario: Notification matches in-app state

- **WHEN** an active stage attempt reaches a persisted outcome
- **THEN** its foreground-service notification and service lifetime end
- **AND** the UI shows the same persisted outcome
- **AND** a normal notification may invite the user to review or trigger the
  next allowed action.

#### Scenario: Attempt completes before foreground-service delivery

- **WHEN** an attempt reaches its persisted outcome before a previously issued
  foreground-service refresh is delivered
- **THEN** the service first acknowledges the foreground start contract
- **AND** it immediately stops foreground execution without crashing the app
- **AND** the normal review, continue, or retry notification remains available.

#### Scenario: Relaunch reconstructs the timeline

- **WHEN** the app restarts with a non-terminal authoring session
- **THEN** the timeline, accepted checkpoints, stale descendants, attempt
  history, and next allowed action are reconstructed from durable state rather
  than an in-memory job flow.

## ADDED Requirements

### Requirement: Durable stage checkpoints and attempts

The application SHALL persist a versioned authoring session, append-only stage
attempts, and explicitly accepted checkpoints for feasibility, algorithm, every
required call function, plan, and render. Each session SHALL freeze the user
instruction, target widget identity when editing, selected model artifact,
inference configuration, authoring protocol/schema versions, and relevant tool
contract digest. Each checkpoint SHALL record an artifact digest and the exact
accepted upstream digest on which it depends.

Only a completed and validated candidate explicitly accepted by the user SHALL
become an authoritative checkpoint. Local assembly validation MAY run
automatically after render is accepted but SHALL use only current, non-stale
checkpoints.

Protocol version and source-fragment identity/signature SHALL be derived from
the session and current stage rather than requested from the model. The plan
checkpoint SHALL be generated deterministically by the application from the
accepted call-function order and SHALL NOT load or invoke the model. New call
functions SHALL return only tool arguments; the application-owned plan SHALL
add the frozen alias, tool id, and contract version. Previously accepted legacy
envelopes and complete-call functions MAY be normalized for compatibility but
SHALL NOT expand authority.

#### Scenario: Successful attempt awaits acceptance

- **WHEN** one stage generation returns an artifact that passes deterministic
  parsing and validation
- **THEN** the application persists it as a reviewable candidate
- **AND** it does not use that candidate as an upstream checkpoint until the
  user chooses Continue.

#### Scenario: Accepted checkpoint unlocks the next stage

- **WHEN** the user accepts a successful candidate
- **THEN** it becomes the authoritative checkpoint for that stage
- **AND** only the next dependency-satisfied stage becomes eligible to run.

#### Scenario: Deterministic plan does not invoke the model

- **WHEN** the user starts the dependency-satisfied plan stage
- **THEN** the application derives and validates its source from the accepted
  call functions and algorithm
- **AND** no device recovery wait, model load, or generation occurs.

#### Scenario: Incomplete write cannot create a checkpoint

- **WHEN** process death or storage failure interrupts attempt persistence
- **THEN** no partial candidate or accepted checkpoint is exposed
- **AND** the last committed session revision remains authoritative.

### Requirement: Inspectable safe execution details

For every attempt, the application SHALL show the stage and revision, attempt
kind and number, status, duration, captured byte count, controlled validation
result, and bounded captured JSON or source when available. Captured content
SHALL be rendered only as escaped plain text and SHALL NOT be executed,
interpreted as rich content, sent to a provider, or treated as a capability
grant merely because it is displayed or persisted.

The user-visible record SHALL exclude chain-of-thought, reasoning tokens, full
internal prompts, secrets, raw exception text, stack traces, model paths, and
unbounded logs. If generation fails before a bounded artifact is captured, the
application SHALL say that no artifact was available instead of fabricating or
exposing unrelated content.

#### Scenario: Inspect a successful code stage

- **WHEN** a call-function, plan, or render candidate is captured and validated
- **THEN** the user can inspect the exact bounded source and successful
  validation summary as inert text before accepting it.

#### Scenario: Inspect a rejected artifact

- **WHEN** a bounded artifact is captured but deterministic validation rejects
  it
- **THEN** the user can inspect the inert captured content and controlled
  failure category
- **AND** no rejected source is executed or included in final assembly.

#### Scenario: Failure occurs before capture

- **WHEN** model loading, timeout, cancellation, or tool-call parsing fails
  before an artifact is captured
- **THEN** the attempt shows the controlled failure and states that captured
  content is unavailable
- **AND** it exposes no raw internal exception or generated token stream.

### Requirement: User-controlled retry and reprocessing

Each explicit user action SHALL authorize at most one model generation. A
failed stage SHALL expose Retry while its persisted repair budget remains. A
retry SHALL receive only immutable accepted dependencies, the relevant checked-
in contract, a controlled failure code, and the immediately preceding bounded
rejected artifact only when the first repair has a precise localized validation
category. Source-envelope and render-execution repairs SHALL omit rejected
source. A second repair SHALL omit the previous artifact for every category.
Restarting the process SHALL NOT reset consumed repair budget.

An accepted stage SHALL expose Reprocess. Reprocessing SHALL create a new stage
revision without overwriting the accepted checkpoint. If the replacement fails
or is abandoned, the previous accepted checkpoint and descendants remain
authoritative. If the user accepts a successful replacement, every transitive
downstream checkpoint SHALL atomically become stale and SHALL be excluded from
context construction, preview, and final assembly until regenerated.

#### Scenario: Retry only the failed stage

- **WHEN** a stage attempt fails deterministic validation and repair budget
  remains
- **THEN** Retry starts exactly one repair attempt for that stage
- **AND** no accepted upstream stage is regenerated or changed.

#### Scenario: Repair budget survives restart

- **WHEN** the app restarts after one or more retries of the current stage
- **THEN** the remaining repair count is unchanged
- **AND** exhausted automatic repair authority cannot be restored by process
  recreation.

#### Scenario: Render execution repair regenerates without source anchoring

- **WHEN** a render candidate fails controlled sandbox execution or a first
  repair is rejected
- **THEN** the next repair receives the checked-in generic render contract and
  controlled failure category without the rejected source
- **AND** each valid alias remains available only as bounded execution data.

#### Scenario: Failed replacement preserves the working graph

- **WHEN** the user reprocesses an accepted stage and the replacement fails
- **THEN** the existing accepted checkpoint and its descendants remain
  authoritative
- **AND** the failed replacement remains visible only in attempt history.

#### Scenario: Accepted replacement invalidates descendants

- **WHEN** the user accepts a replacement for an upstream stage
- **THEN** every transitive downstream checkpoint is marked stale in the same
  durable transition
- **AND** stale content cannot be used to build later prompts or a widget draft.

### Requirement: Normalized feasibility and actionable presentation validation

For an achievable feasibility result, the model-facing contract SHALL request
a concise display title of at most 80 characters. A non-blank overlong title
SHALL be normalized deterministically by the application; a blank title SHALL
remain invalid. The render contract SHALL enumerate exact node shapes, allowed
tones, and explicit success, empty, and failed-outcome handling before payload
access.

Presentation validation SHALL expose bounded controlled categories for invalid
node type, invalid fields, invalid tone, invalid link provenance, missing
empty-result handling, and missing failed-outcome handling. It SHALL NOT expose
raw JavaScript exceptions or generated content through those categories.

The render request SHALL contain a stage-specific generic API contract. It SHALL
describe outcomes as a plain JavaScript object accessed with bracket notation,
forbid Map-style `get()`, and require aliases to be copied from `toolResults`.
Generic contract examples SHALL NOT embed a concrete domain alias. Sandbox
script failure, resource limit, and runtime unavailability SHALL remain
distinct controlled categories from static source-envelope rejection.

#### Scenario: Long feasible message does not consume a repair

- **WHEN** an otherwise-valid achievable result contains a non-blank display
  name longer than 80 characters
- **THEN** the application normalizes it to a bounded display name
- **AND** the feasibility attempt may succeed without another generation.

#### Scenario: Render reads a failed outcome payload

- **WHEN** the render function fails the synthetic failed-outcome fixture
  before returning a presentation
- **THEN** validation reports the controlled failed-outcome-handling category
- **AND** Retry receives targeted checked-in guidance to branch on status before
  reading payload.

#### Scenario: Render request is preflighted before model load

- **WHEN** the application constructs a render request using the longest valid
  algorithm alias
- **THEN** the stage objective remains within its checked-in text limit
- **AND** exact node shapes remain present in the bounded render context
- **AND** any local request-construction failure is persisted before device
  recovery or model load and is not reported as runtime unavailability.

#### Scenario: Generic render contract binds an execution alias

- **WHEN** the render request is built for any accepted tool-call algorithm
- **THEN** its generic API contract contains no concrete alias example
- **AND** the exact allowed aliases are supplied separately in `toolResults`
- **AND** the contract requires `outcomes[alias]` rather than `outcomes.get()`.

### Requirement: Private resumable authoring data

Persisted authoring instructions, candidates, rejected artifacts, validation
results, and checkpoints SHALL remain in app-private local storage, SHALL be
excluded from backup, normal Chat history, execution logs, analytics, and
automatic upload, and SHALL obey checked-in size limits. Tool credentials and
provider results SHALL NOT enter persisted authoring stage data.

Final widget confirmation SHALL remain a separate explicit action after current
checkpoints pass exact assembly validation and preview. Confirming SHALL persist
the normalized widget revision and remove the transient authoring session;
discarding SHALL remove the transient session without creating or changing a
widget.

#### Scenario: Workflow approval is not widget consent

- **WHEN** the user accepts a stage candidate or continues to the next stage
- **THEN** no widget is installed, enabled, scheduled, or granted capabilities
- **AND** final preview and confirmation remain required.

#### Scenario: Confirm and remove transient state

- **WHEN** the user confirms a fully validated preview
- **THEN** the application atomically persists the normalized widget revision
  and consent record
- **AND** removes the transient session, candidates, rejected artifacts, and
  attempt history.

#### Scenario: Discard and remove transient state

- **WHEN** the user discards an inactive authoring session
- **THEN** all of its persisted transient content is removed
- **AND** no confirmed widget revision is created or modified.
