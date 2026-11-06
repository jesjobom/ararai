## MODIFIED Requirements

### Requirement: Application-scoped authoring jobs

The application SHALL run each user-authorized widget-authoring stage attempt
as an application-scoped job that continues while the user navigates within the
app and survives leaving the authoring screen while the process lives. The job
SHALL use the shared application-scoped local LLM engine, SHALL NOT create a
second concurrent engine instance, and SHALL persist the attempt and unload the model before continuing to another validated stage. The user-started workflow SHALL stop at a failure, cancellation, terminal feasibility outcome, invalid assembly, unavailable configuration, or draft preview.

Previously accepted checkpoints SHALL remain durable when an active attempt is
cancelled, interrupted, or killed with the process. The application SHALL NOT
infer a successful artifact from an attempt that did not transactionally reach
its completed state.

#### Scenario: Generation continues after leaving the screen

- **WHEN** the user starts widget authoring and navigates away without cancelling
- **THEN** validated stages keep running at application scope until a stop condition
- **AND** returning to the authoring screen shows the same durable session and
  attempt state.

#### Scenario: Process death is reported, not silently ignored

- **WHEN** the process dies while a later stage attempt is in flight
- **THEN** the in-flight attempt is reported as interrupted after restart
- **AND** every previously accepted checkpoint and consumed retry count remains
  available
- **AND** the user may retry the interrupted stage without repeating accepted
  upstream stages.

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

After every generation outcome, the application SHALL persist the attempt and unload the model. A successful validated outcome SHALL allow the next stage to start under the original user request; failure, timeout, cancellation, or recovery timeout SHALL end foreground execution without advancing.

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

- **WHEN** a model generation fails validation, times out, is cancelled, or has no next stage
- **THEN** the application persists its outcome and unloads the model
- **AND** no later generation begins without another user action.

### Requirement: Dedicated prompt-based widget authorship

The application SHALL let a user ask an explicitly verified installed local
model to create or edit an in-app widget in a dedicated authoring flow. The
application SHALL expose a bounded sequence of isolated, validated model stages for
feasibility/capability selection, typed algorithm design, JavaScript function
generation, orchestration, presentation, validation, and deterministic
assembly. Each stage SHALL receive only the bounded non-secret context and
previously validated artifacts required for its objective.

The bounded context SHALL state whether the dedicated flow is creating or
editing a widget and SHALL define the user's instruction as the desired widget
behavior/content. The application SHALL NOT require the user to repeat the
current UI operation or the word "widget" in that instruction.

#### Scenario: Propose a new widget

- **WHEN** the user asks an eligible installed model to create a supported
  widget
- **THEN** the authoring flow automatically advances through the versioned pipeline after each validated stage and
  captures one typed artifact from each required stage
- **AND** user-specific call functions and custom presentation JavaScript remain
  model-generated, while application-owned orchestration and simple collection
  cards may be compiled deterministically from accepted typed model artifacts
- **AND** the application, not the model, assigns identity, revision, digest,
  effective grants, hard resource limits, assembly order, and consent.

#### Scenario: Interpret behavior-only creation language

- **WHEN** the create-widget flow receives an instruction such as "show a
  historical event for today" without restating "create a widget"
- **THEN** every stage treats it as a widget-creation request
- **AND** does not request clarification merely because the operation or object
  type was supplied by the UI context instead of repeated by the user.

#### Scenario: Authoring model is unavailable

- **WHEN** no installed selected model is explicitly verified for widget
  authorship
- **THEN** creation and prompt-based editing are unavailable with a clear
  explanation
- **AND** listing, inspecting, enabling, disabling, refreshing, and deleting
  existing widgets remain available.

### Requirement: Recover the device between model generations

Each user-authorized stage or retry SHALL begin with the model unloaded, await a
bounded stable temperature-and-memory gate, and load the model with the fixed
authoring inference configuration. After persisting that attempt outcome, the
application SHALL unload the model before any next validated stage begins.
Unload, wait, and replacement publication SHALL be serialized with model
lifecycle transitions. Native ownership transfer SHALL remain non-cancellable,
the wait SHALL remain
cancellable, and the UI plus sanitized lifecycle telemetry SHALL expose the
recovery and reload phases. The application SHALL NOT recover again for
terminal `unachievable` or `needs_clarification` outcomes.

#### Scenario: Start each later generation from a recovered model session

- **WHEN** a validated stage advances automatically or the user retries a rejected stage
- **THEN** the application waits while the model remains unloaded
- **AND** it publishes a freshly loaded text-only session using the same fixed
  authoring configuration only after the recovery gate is stable
- **AND** the next stage or repair starts only after that reload succeeds.

#### Scenario: Avoid a reload for a terminal feasibility outcome

- **WHEN** feasibility is validated as `unachievable` or
  `needs_clarification`
- **THEN** the attempt returns the controlled outcome without reloading the
  model or requesting an algorithm.

#### Scenario: Stop safely when recovery does not stabilize

- **WHEN** the bounded recovery gate does not observe stable temperature and
  memory conditions before its deadline
- **THEN** the authoring attempt ends with a controlled timeout while the model
  remains unloaded
- **AND** no later model request, provider call, checkpoint promotion, or
  capability expansion occurs.

#### Scenario: Fail safely when the replacement session cannot load

- **WHEN** the feasibility-to-algorithm reload fails before a replacement model
  session is published
- **THEN** the authoring attempt ends with the controlled model-load failure
- **AND** no algorithm request, provider call, checkpoint promotion, or
  capability expansion occurs.

#### Scenario: Repair an inconsistent feasibility outcome

- **WHEN** the feasibility artifact contains an invalid outcome-dependent
  message or tool ID selection, or a legacy artifact contains an invalid schedule
- **THEN** the application identifies the controlled feasibility category
- **AND** the repair repeats the session-version-appropriate field contract
  before requesting one replacement artifact.

#### Scenario: Reject unsupported adaptive execution

- **WHEN** the proposed algorithm requires a later tool call whose arguments
  depend on an earlier live provider result
- **THEN** the application rejects it as unsupported by the v1 plan-all runtime
- **AND** does not approximate, reorder, or silently remove the dependency.

#### Scenario: Normalize irrelevant non-tool metadata

- **WHEN** a `runtime_input` or `transform` step carries a non-null `toolId` or
  `contractVersion` value in the shared bounded step shape
- **THEN** the application discards those non-authoritative values and retains
  the step with no tool capability
- **AND** still validates the exact field set and every real `tool_call`
  identity/version against the frozen envelope.

### Requirement: Durable stage checkpoints and attempts

The application SHALL persist a versioned authoring session, append-only stage
attempts, and explicitly accepted checkpoints for feasibility, algorithm, every
required call function, plan, and render. Each session SHALL freeze the user
instruction, target widget identity when editing, selected model artifact,
inference configuration, authoring protocol/schema versions, and relevant tool
contract digest. Each checkpoint SHALL record an artifact digest and the exact
accepted upstream digest on which it depends.

Only a completed and validated candidate accepted by the application-owned workflow SHALL
become an authoritative checkpoint. Automatic acceptance SHALL match the completed attempt and session revision. Local assembly validation MAY run
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

Starting a previously pending stage or accepting a reviewable candidate MAY be presented
as a manual Advance action. Acceptance SHALL remain the only transition that makes
a candidate an authoritative checkpoint, and final widget confirmation SHALL stay explicit.

#### Scenario: Successful attempt awaits acceptance

- **WHEN** one stage generation returns an artifact that passes deterministic
  parsing and validation
- **THEN** the application persists it as a reviewable candidate
- **AND** automatic progression accepts it before using it as an upstream checkpoint.

#### Scenario: Accepted checkpoint unlocks the next stage

- **WHEN** the workflow accepts a successful candidate automatically or the user resumes it manually
- **THEN** it becomes the authoritative checkpoint for that stage
- **AND** only the next dependency-satisfied stage becomes eligible to run.

#### Scenario: Deterministic plan does not invoke the model

- **WHEN** the dependency-satisfied plan stage starts
- **THEN** the application derives and validates its source from the accepted
  call functions and algorithm
- **AND** no device recovery wait, model load, or generation occurs.

#### Scenario: Incomplete write cannot create a checkpoint

- **WHEN** process death or storage failure interrupts attempt persistence
- **THEN** no partial candidate or accepted checkpoint is exposed
- **AND** the last committed session revision remains authoritative.

#### Scenario: Advance through stages

- **WHEN** a pending stage has no candidate
- **THEN** Advance starts that stage
- **AND** when the resulting candidate awaits review, Advance accepts it and
  unlocks the next stage.

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

The stage summary SHALL initially show status, latest attempt outcome and
duration, and total attempt count. The user SHALL be able to expand each stage
to inspect every attempt and its inert captured response. Contextual actions
SHALL appear immediately below their stage, outside the expanded detail.

#### Scenario: Inspect a successful code stage

- **WHEN** a call-function, plan, or render candidate is captured and validated
- **THEN** the user can inspect the exact bounded source and successful
  validation summary as inert text during or after automatic progression.

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

#### Scenario: Inspect a compact stage

- **WHEN** a stage has multiple completed attempts
- **THEN** its card shows a concise latest outcome, duration, and count
- **AND** expanding it reveals every attempt and captured response.

### Requirement: User-controlled retry and reprocessing

Starting authoring SHALL authorize successive validated stages, but each explicit Retry SHALL authorize at most one repair generation for the failed stage. A
failed stage SHALL expose Retry without a cumulative manual attempt limit. A
retry SHALL receive only immutable accepted dependencies, the relevant checked-
in contract, a controlled failure code, and the immediately preceding rejected
artifact when a localized validation failure is actionable and the complete
artifact fits a 2 KiB prompt-evidence bound and the stage input budget. This
applies to every retry, not only the first. Source-envelope and render-execution
repairs, generic schema failures, runtime failures, missing artifacts, timeouts,
and oversized evidence SHALL omit the previous artifact.
Every repair prompt SHALL include its monotonically increasing attempt number
for the current stage as non-authoritative variation guidance. The attempt
number SHALL NOT alter validated context, tool schema, or accepted dependencies.
Retry SHALL NOT repeat a failed repair automatically.

An accepted stage or a successful unaccepted candidate SHALL expose Reprocess.
Reprocessing SHALL create a new stage revision without overwriting any accepted
checkpoint. If the replacement fails
or is abandoned, the previous accepted checkpoint and descendants remain
authoritative. If the user accepts a successful replacement, every transitive
downstream checkpoint SHALL atomically become stale and SHALL be excluded from
context construction, preview, and final assembly until regenerated.

#### Scenario: Retry only the failed stage

- **WHEN** a stage attempt fails deterministic validation and the user chooses Retry
- **THEN** Retry starts exactly one repair attempt for that stage
- **AND** no accepted upstream stage is regenerated or changed.

#### Scenario: Repair budget survives restart

- **WHEN** the app restarts after one or more retries of the current stage
- **THEN** prior attempts remain persisted
- **AND** the user can still retry the failed stage.

#### Scenario: Consecutive repairs carry distinct attempt numbers

- **WHEN** the second and third attempts of one stage are generated
- **THEN** their prompts identify attempt 2 and attempt 3 respectively
- **AND** both may include only the immediately preceding complete bounded artifact when its validation failure is actionable
- **AND** both retain the same validated stage contract.

#### Scenario: Oversized repair evidence is omitted

- **WHEN** a localized rejected artifact exceeds the evidence bound or would exceed the stage input budget
- **THEN** the retry retains its attempt number and controlled failure code
- **AND** it omits the artifact without truncating it into invalid JSON or source.

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

#### Scenario: Continue after repeated failures

- **WHEN** a stage fails after more than three user-authorized attempts
- **THEN** the user can still request one more attempt
- **AND** previous attempts remain inspectable.

#### Scenario: Reprocess successful candidate before accepting it

- **WHEN** the current stage has a successful candidate awaiting review
- **THEN** the user can start a new stage revision without accepting that
  candidate first.

### Requirement: One-tap continuation after stage review

Starting or retrying a stage SHALL continue automatically across subsequent validated stages while the process lives. The existing Avançar action MAY resume a previously reviewable candidate. Automatic progression SHALL stop at the first failure, cancellation, terminal feasibility outcome, unavailable configuration, invalid assembly, or final draft preview. Confirmation of the widget and its authority remains explicit.

#### Scenario: Continue to the next stage

- **WHEN** a successful stage has a next stage and valid model configuration
- **THEN** the current artifact is durably accepted and the next attempt is submitted automatically
- **AND** that next attempt remains cancellable and subject to normal single-flight and device-state checks.

#### Scenario: No next stage or non-applied acceptance

- **WHEN** feasibility cannot proceed, final render is ready for preview, or the acceptance was not applied
- **THEN** no further model attempt is started.

#### Scenario: Configuration unavailable

- **WHEN** a successful stage finishes but the authoring model configuration is unavailable
- **THEN** the candidate remains reviewable and no next stage starts, without losing accepted work.
