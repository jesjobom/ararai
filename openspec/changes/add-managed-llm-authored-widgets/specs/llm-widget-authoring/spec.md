## Purpose

Define how an eligible local model proposes new or edited widget programs from
user prompts while application validation, preview, consent, identity, and
activation remain authoritative.

## ADDED Requirements

### Requirement: Dedicated prompt-based widget authorship

The application SHALL let a user ask an explicitly verified installed local
model to create or edit an in-app widget in a dedicated authoring flow. The
application SHALL coordinate a bounded sequence of isolated model stages for
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
- **THEN** the authoring flow advances through the versioned pipeline and
  captures one typed artifact from each required stage
- **AND** accepted JavaScript fragments remain model-generated
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

### Requirement: Validate feasibility and a typed algorithm before code

The first stage SHALL return exactly one `outcome`, one bounded
outcome-dependent `message`, one bounded periodic interval or null, and the
minimum registered tool IDs. The application SHALL derive protocol metadata,
enablement, eligible registered tool versions, deterministic runtime grants,
allowlisted presentation grants, and terminal normalization from checked-in
policy. For an achievable request, a later stage SHALL return a bounded typed
acyclic algorithm whose steps and dependencies distinguish runtime inputs,
independent planned tool calls, transformations, and the final presentation
objective. The application SHALL validate both artifacts before requesting
JavaScript.

Tool identity SHALL be authoritative only for a `tool_call` step. The
application SHALL resolve that ID to the exact registered version already
frozen by feasibility and SHALL NOT ask the model to repeat a contract version.
It SHALL accept and discard legacy `contractVersion` wire values without
granting authority, while continuing to reject unknown fields, invented tool
IDs, and capability expansion on actual tool calls.

Per-step runtime grants SHALL NOT be a model decision. The application SHALL
derive runtime authority exclusively from the validated feasibility envelope
and registered contracts, SHALL omit `runtimeInputs` from the algorithm capture
schema and normalized artifact, and MAY accept and discard that field from
legacy wire artifacts without granting any capability. Tool metadata SHALL be
required only for `tool_call` steps; its omission from other step kinds SHALL
not invalidate the algorithm.

#### Scenario: Derive the frozen tool version

- **WHEN** an algorithm selects a tool ID that exists in the frozen feasibility
  envelope and omits `contractVersion`
- **THEN** the application resolves the exact frozen capability and version
  for the normalized tool-call step
- **AND** a legacy wire version, whether absent, matching, or inconsistent,
  cannot alter that application-owned selection.

The feasibility instruction SHALL state the four-field conditional contract
for all three outcomes: `message` is the display name for `achievable`, the
reason for `unachievable`, and one question for `needs_clarification`. It SHALL
ask the model to select only the minimum required available tool IDs without
treating not-yet-validated selections as frozen. A bounded repair SHALL
translate its controlled failure code into an actionable checked-in correction
without including arbitrary validator or exception text.
The wire artifact SHALL NOT require separate `reason` or
`clarificationQuestion` fields; the application SHALL derive both nullable
values from `outcome` and `message`.

#### Scenario: Accept a feasible capability plan

- **WHEN** the model selects only registered `WIDGET`-eligible tool IDs for an
  achievable request
- **THEN** the application resolves eligible versions, derives bounded runtime
  and presentation grants, and freezes that validated capability envelope for
  every later stage and repair
- **AND** later artifacts cannot broaden it.

#### Scenario: Normalize legacy runtime labels

- **WHEN** an otherwise valid algorithm includes a legacy `runtimeInputs`
  field with derived labels such as `month` or `day`
- **THEN** the application discards the entire field and continues using only
  runtime grants derived from the frozen feasibility envelope
- **AND** the normalized algorithm passed to later stages contains no
  model-authored per-step runtime grants.

#### Scenario: Report an unavailable operation

- **WHEN** the request requires an operation for which no registered eligible
  tool or supported runtime capability exists
- **THEN** the model may return `unachievable` with a bounded controlled
  explanation
- **AND** the application shows the outcome without generating code, persisting
  state, or invoking a provider.

#### Scenario: Request clarification

- **WHEN** the request is too ambiguous to freeze a safe capability envelope
- **THEN** the model may return `needs_clarification` with one bounded question
- **AND** the attempt ends so the user can revise the prompt explicitly.

### Requirement: Recover the device between model generations

After the first feasibility generation and before every subsequent model
generation, including a repair, the application SHALL unload the selected local
model, await a bounded stable temperature-and-memory gate, and reload the model
with the fixed authoring inference configuration. Unload, wait, and replacement
publication SHALL be serialized with model lifecycle transitions. Native
ownership transfer SHALL remain non-cancellable, the wait SHALL remain
cancellable, and the UI plus sanitized lifecycle telemetry SHALL expose the
recovery and reload phases. The application SHALL NOT recover again for
terminal `unachievable` or `needs_clarification` outcomes.

#### Scenario: Start each later generation from a recovered model session

- **WHEN** a valid artifact or rejected attempt requires another model generation
- **THEN** the application closes the current model session and waits while the
  model remains unloaded
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
- **AND** no later model request, provider call, persistence, or capability
  expansion occurs.

#### Scenario: Fail safely when the replacement session cannot load

- **WHEN** the feasibility-to-algorithm reload fails before a replacement model
  session is published
- **THEN** the authoring attempt ends with the controlled model-load failure
- **AND** no algorithm request, provider call, persistence, or capability
  expansion occurs.

#### Scenario: Repair an inconsistent feasibility outcome

- **WHEN** the feasibility artifact contains an invalid outcome-dependent
  message, schedule, or tool ID selection
- **THEN** the application identifies the controlled feasibility category
- **AND** the repair repeats the four-field contract and keeps a supported
  request on `achievable` before requesting one replacement artifact.

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

### Requirement: Generate and validate dynamic JavaScript by responsibility

For every validated planned tool call, the model SHALL generate one bounded
named JavaScript function that receives only the deterministic runtime stdlib
snapshot and declared inputs and returns one semantic call for the fixed
registered tool/version. Separate stages SHALL generate a short `plan`
orchestrator and a `render` function. The application SHALL syntax-check and
execute each fragment with synthetic normal/boundary fixtures, validate tool
arguments and presentation output against current contracts, and accept no
fragment that uses undeclared inputs or capabilities.

The bounded program API context SHALL map natural requests for a random,
varied, shuffled, or selected item from a bounded list, including equivalent
supported user-language terms, to the `seed` runtime grant and
`runtime.seededIndex(length)`. It SHALL state that `Math.random` is unavailable,
that a fresh execution may select a different item, and that the same runtime
snapshot must reproduce the same index.

#### Scenario: Translate natural variation safely

- **WHEN** the user asks for one random or varied item from a bounded tool
  result without naming an implementation primitive
- **THEN** an achievable artifact selects the `seed` runtime grant
- **AND** generated code uses `runtime.seededIndex(length)` rather than
  `Math.random` or another ambient source of nondeterminism.

#### Scenario: Validate one generated tool-call function

- **WHEN** a generated function returns a valid call for its frozen
  tool/version across bounded synthetic runtime fixtures
- **THEN** the application stores that exact fragment only in the current
  in-memory attempt
- **AND** later model stages receive its immutable signature and required
  behavior without authority to rewrite it.

#### Scenario: Resolve current time dynamically

- **WHEN** generated behavior needs the current local date or time
- **THEN** it reads the fresh immutable execution snapshot through the granted
  deterministic runtime stdlib
- **AND** does not bake the authoring date into source or read an ambient clock.

#### Scenario: Validate orchestration and presentation separately

- **WHEN** all required call functions are accepted
- **THEN** the application validates the generated `plan` orchestration against
  the typed algorithm and validates `render` against synthetic success,
  failure, and empty tool outcomes
- **AND** an invalid presentation cannot invalidate or rewrite an accepted call
  function.

### Requirement: Assemble deterministically and repair only the failing stage

The application SHALL assemble the final source from exact accepted
model-generated fragments in a fixed application-owned order and SHALL perform
a complete synthetic dry-run of the exact package before preview. A later model
round MUST NOT rewrite accepted fragments. The complete attempt SHALL have
checked-in generation-count, artifact-size, per-stage timeout, total-deadline,
and repair limits. At most two repair generations SHALL be allowed across the
attempt, each scoped to one failed stage and unable to expand the frozen
capability envelope.

#### Scenario: Repair a rejected fragment

- **WHEN** a fragment fails a deterministic validator and repair budget remains
- **THEN** the application supplies only that artifact, its immutable
  dependencies, the relevant contract, and a controlled failure code to one
  repair stage
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

- **WHEN** the user selects the cold complete-pipeline diagnostic
- **THEN** the application runs only the natural-request staged pipeline using
  the same compact feasibility context as production
- **AND** runs no schema characterization case or standalone feasibility probe
  before it
- **AND** applies the same validation, repair, deadline, sanitization, and
  no-persistence rules as the complete matrix pipeline case.

#### Scenario: Observe every complete-pipeline attempt without content

- **WHEN** a complete diagnostic pipeline runs or repairs any stage
- **THEN** its sanitized report records the stage, stage-local attempt, repair
  flag, controlled outcome, and monotonic first-event, capture, terminal,
  watchdog, return, and cleanup-overrun timings for that round
- **AND** a round with no callback remains distinguishable from captured but
  invalid output
- **AND** no prompt, context, generated argument, source, provider result,
  exception text, or stack trace enters the normal report or controlled Logcat.

#### Scenario: Exhaust repair budget

- **WHEN** two repair generations have been used or the overall deadline is
  reached without a valid assembled package
- **THEN** the attempt ends with a controlled stage-specific result
- **AND** persists, schedules, and executes nothing.

#### Scenario: Dry-run the exact assembled package

- **WHEN** all required fragments pass their local validators
- **THEN** the application assembles them without model rewriting and executes
  the exact package with synthetic runtime and tool outcomes
- **AND** only a successful plan/render dry-run may proceed to draft preview.

### Requirement: Validate before preview or persistence

Every stage artifact and the exact assembled package SHALL pass strict schema,
source/package, frozen-capability, tool/version, schedule, presentation,
provenance, and resource validation before the draft can be confirmed.
Authoring MUST NOT execute a production tool, contact a provider, persist a
revision, register work, or activate a widget.

#### Scenario: Preview a valid proposal

- **WHEN** a proposal passes all validation
- **THEN** the application shows a normalized behavior summary, source,
  planned tool/capability permissions, schedule, data retention, and expected
  presentation scope
- **AND** labels that view as an unconfirmed draft.

#### Scenario: Reject invented or unsafe behavior

- **WHEN** a proposal references an unknown or ineligible tool, arbitrary
  endpoint, unsupported API, forbidden capability, invalid source, unsafe
  presentation action, or excessive limit
- **THEN** the application rejects it with a controlled user-facing reason
- **AND** persists and executes nothing from the proposal.

#### Scenario: Model returns no valid stage artifact

- **WHEN** a stage ends, fails, times out, or is cancelled without exactly one
  valid structured artifact and no bounded repair succeeds
- **THEN** the authoring attempt ends without changing any widget
- **AND** the UI identifies the failed stage with a controlled reason.

### Requirement: Observable cancellable authoring progress

The foreground authoring UI SHALL expose the active bounded stage, call-function
index/count when applicable, validation activity, and repair attempt/count. It
SHALL permit cancellation throughout generation and validation without
presenting intermediate source as a confirmable draft.

Debug runtime diagnostics SHALL emit sanitized process-local lifecycle events
that distinguish engine initialization/reuse, conversation creation/reuse,
request submission, first/terminal callback, cancellation, close, cleanup, and
overlapping active generations. The events SHALL include bounded timing plus
available thread-count, RSS, swap, total/native PSS, native/Java heap, available
system memory, low-memory state, and thermal-status signals, and SHALL NOT
include model paths or identifiers, prompts, generated content, tool arguments,
exception messages, provider data, or stack traces. Disposed conversation
tracking SHALL preserve identity-based idempotence without keeping discarded
native wrappers strongly reachable.

#### Scenario: Show multi-round progress

- **WHEN** an authoring attempt advances between stages
- **THEN** the UI updates among feasibility, device recovery, model reload,
  tool selection, algorithm design, call generation N/M, orchestration,
  presentation, validation, repair, and draft-ready states
- **AND** does not imply that an intermediate artifact is saved or executable.

#### Scenario: Cancel during a later stage

- **WHEN** the user cancels or navigates away during any generation, repair, or
  validation stage
- **THEN** active work and ephemeral conversations are released, late callbacks
  are discarded, and all intermediate artifacts are cleared
- **AND** no widget or revision is changed.

#### Scenario: Diagnose a native stage stall

- **WHEN** a debug authoring round times out before its first generation event
- **THEN** local runtime telemetry identifies whether the engine was reloaded,
  a conversation was created or reused, another generation overlapped, and
  where cancel/close/cleanup time was spent
- **AND** reports only process-local identifiers and sanitized resource signals.

#### Scenario: Observe ephemeral conversation release

- **WHEN** an ephemeral debug conversation is cancelled or closed
- **THEN** telemetry captures sanitized process-memory checkpoints before and
  after the operation so native heap and PSS release can be compared
- **AND** the discarded wrapper remains eligible for garbage collection after
  identity-based duplicate-disposal protection is no longer needed.

### Requirement: Explicit confirmation and least privilege

The application SHALL derive the effective grant from the validated proposal,
available tool contracts, fixed policy, and the user's confirmation. The user
MUST explicitly confirm creation or any edit before it becomes active, and the
confirmation SHALL disclose background frequency, network tools, retained
history, and user actions. No proposal may silently enable a tool or provider.

#### Scenario: Confirm and enable a draft

- **WHEN** the user accepts a valid draft through an action that explicitly
  states it will create and enable the disclosed schedule
- **THEN** the application persists the new revision and consent record
- **AND** schedules only the confirmed effective grant.

#### Scenario: Decline a draft

- **WHEN** the user cancels or leaves an unconfirmed draft
- **THEN** no widget, revision, consent, schedule, or production tool call is
  created.

#### Scenario: Required tool is disabled

- **WHEN** a valid draft requires a registered tool that the user has not
  enabled
- **THEN** the application keeps the draft blocked from activation
- **AND** offers an explicit enablement path without changing tool state
  silently.

### Requirement: Controlled prompt-based editing

Editing SHALL supply the relevant stages with the current normalized managed
definition and active source but not unrelated widget data, credentials, raw
execution logs, or authoring history. The assembled result SHALL be a complete
replacement draft, and the application SHALL show a revision diff and highlight
added tools, capabilities, data retention, presentation actions, or increased
frequency before confirmation.

#### Scenario: Propose a safe edit

- **WHEN** the user asks to change an existing widget's title, logic,
  presentation, or supported schedule
- **THEN** the application validates a complete replacement proposal
- **AND** shows its behavior/source diff while retaining application-owned
  widget identity and ownership.

#### Scenario: Expand authority during editing

- **WHEN** an edit requests an additional tool, capability, retained data,
  external action, or more frequent schedule
- **THEN** the preview calls out the expansion explicitly
- **AND** the previous consent cannot authorize the new revision.

### Requirement: No unattended model execution

The language model SHALL participate only in an explicit foreground authoring
attempt. Confirmed widget refresh, rendering, history lookup, recovery, and
scheduling MUST use the stored validated program and MUST NOT invoke a model to
choose, repair, or reinterpret behavior.

#### Scenario: Refresh an LLM-authored widget

- **WHEN** a confirmed widget executes manually or in the background
- **THEN** the stored active program executes without constructing an
  authoring conversation or loading a model.

#### Scenario: Stored program later becomes invalid

- **WHEN** an active revision becomes incompatible or fails validation
- **THEN** the application marks it as needing attention
- **AND** does not ask a model to change it until the user explicitly starts an
  edit.

### Requirement: Local private authoring data

Authoring prompts, intermediate artifacts, draft source, validation output,
repair context, and model protocol SHALL remain local and app-private. The
dedicated authoring exchanges MUST NOT be added to normal Chat history,
execution logs, sanitized diagnostics, analytics, or backups, and tool
credentials MUST NOT enter model context or stage fields. The only export
exception SHALL be an explicitly confirmed raw sidecar for a user-initiated
diagnostic in a debuggable build; it MUST remain unavailable in release builds
and MUST NOT create an automatic upload or telemetry path.

#### Scenario: Finish an authoring attempt

- **WHEN** an attempt succeeds, fails, or is cancelled
- **THEN** every ephemeral stage conversation and in-memory artifact is released
- **AND** only a user-confirmed normalized program revision and consent record
  may become durable.

#### Scenario: Explicitly export a raw local diagnostic

- **WHEN** a user running a debuggable build explicitly confirms the raw-data
  warning after a local diagnostic
- **THEN** the application writes a replace-on-export cache file containing the
  exact bounded stage inputs and captured tool-argument strings
- **AND** shares it only through a temporary read grant while leaving the
  normal report and Logcat sanitized
- **AND** a release build exposes no equivalent control or raw capture.
