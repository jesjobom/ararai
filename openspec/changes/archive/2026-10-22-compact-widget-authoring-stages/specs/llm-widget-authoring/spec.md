## MODIFIED Requirements

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

#### Scenario: Inspect a compact stage

- **WHEN** a stage has multiple completed attempts
- **THEN** its card shows a concise latest outcome, duration, and count
- **AND** expanding it reveals every attempt and captured response.

### Requirement: User-controlled retry and reprocessing

Each explicit user action SHALL authorize at most one model generation. A
failed stage SHALL expose Retry without a cumulative manual attempt limit. A
retry SHALL receive only immutable accepted dependencies, the relevant checked-
in contract, a controlled failure code, and the immediately preceding bounded
rejected artifact only when the first repair has a precise localized validation
category. Source-envelope and render-execution repairs SHALL omit rejected
source. All later repairs SHALL omit the previous artifact for every category.
Retry SHALL NOT start an automatic loop.

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

Starting a pending stage and accepting a reviewed candidate SHALL be presented
as one context-dependent action named Advance. Acceptance SHALL remain the
only transition that makes a candidate an authoritative checkpoint.

#### Scenario: Successful attempt awaits acceptance

- **WHEN** one stage generation returns an artifact that passes deterministic
  parsing and validation
- **THEN** the application persists it as a reviewable candidate
- **AND** it does not use that candidate as an upstream checkpoint until the
  user chooses Advance.

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

#### Scenario: Advance through stages

- **WHEN** a pending stage has no candidate
- **THEN** Advance starts that stage
- **AND** when the resulting candidate awaits review, Advance accepts it and
  unlocks the next stage.

### Requirement: Assemble deterministically and repair only the failing stage

The application SHALL assemble the final source from exact accepted source
fragments in a fixed application-owned order and SHALL perform
a complete synthetic dry-run of the exact package before preview. A later model
round MUST NOT rewrite accepted fragments. Each user-authorized stage attempt
SHALL have checked-in artifact-size and timeout limits; manual stage retries SHALL have no fixed count limit. A
repair SHALL require an explicit user action, remain scoped to the failed stage,
and never expand the frozen
capability envelope or rewrite an accepted checkpoint.

#### Scenario: Repair a rejected fragment

- **WHEN** a fragment fails deterministic validation and the user chooses Retry
- **THEN** the application supplies immutable accepted dependencies, the
  relevant contract, a controlled failure code, and the rejected artifact only
  when a first repair has a localized validation category
- **AND** revalidates the replacement without exposing provider data, secrets,
  stack traces, or unrelated artifacts.

#### Scenario: Localize a feasibility rejection

- **WHEN** deterministic feasibility validation rejects an artifact
- **THEN** the application classifies it as JSON/root, exact field set,
  protocol, display name, enabled value, schedule, tool selection, runtime
  grant, presentation grant, outcome contract, or resource limit
- **AND** uses that controlled category in the bounded repair and sanitized
  diagnostic, recording its attempt number and argument size while exporting
  neither the rejected value nor arbitrary validator detail
- **AND** a debug build may emit only the same controlled attempt metadata to a
  dedicated Logcat tag for user-initiated local ADB collection.

#### Scenario: Isolate feasibility timeout factors

- **WHEN** the user selects a local feasibility diagnostic probe
- **THEN** the application runs exactly one fresh-conversation feasibility
  generation using either full context plus the natural request, compact
  feasibility context plus the natural request, or compact context plus an
  explicit implementation control
- **AND** the report identifies the selected mode and includes only sanitized
  input byte counts, estimated input characters, context hash, controlled
  outcome/capture metadata, and monotonic first-event, capture, terminal,
  watchdog, return, and cleanup-overrun timings
- **AND** excludes the prompt, context text, generated arguments, raw model
  output, source, exception text, and stack trace.

#### Scenario: Isolate the production-like algorithm stage

- **WHEN** the user selects the isolated natural algorithm diagnostic
- **THEN** the application runs exactly one fresh-conversation algorithm
  generation from an application-owned, normalized achievable feasibility
  fixture using the production natural prompt, context, schema, and parser
- **AND** invokes no provider and persists, schedules, or executes nothing
- **AND** reports only sanitized input/lifecycle signals, controlled outcome,
  and aggregate callback metadata while excluding prompt, context, generated
  arguments, provider data, source, exception text, and stack trace.

#### Scenario: Run the production-like pipeline from a cold process

- **WHEN** the user selects a cold authoring diagnostic from a fresh process
- **THEN** it exercises only an explicitly requested stage or isolated probe,
  without automatically running the complete authoring pipeline
- **AND** it applies the production stage contract, validation, and
  sanitization without creating a confirmed widget or schedule.

#### Scenario: Observe every complete-pipeline attempt without content

- **WHEN** a legacy complete-pipeline diagnostic record is inspected
- **THEN** its sanitized report distinguishes stages, attempt outcomes, and
  callback timing without exposing prompt, generated source, provider result,
  exception text, or stack trace
- **AND** production authoring does not use that continuous diagnostic path.

#### Scenario: Exhaust repair budget

- **WHEN** the current stage repeatedly fails without a valid candidate
- **THEN** it records a controlled stage-specific result and awaits user action
- **AND** no widget revision is confirmed, scheduled, or executed.

#### Scenario: Dry-run the exact assembled package

- **WHEN** all required fragments pass their local validators
- **THEN** the application assembles them without model rewriting and executes
  the exact package with synthetic runtime and tool outcomes
- **AND** only a successful plan/render dry-run may proceed to draft preview.
