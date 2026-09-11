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

#### Scenario: Propose a new widget

- **WHEN** the user asks an eligible installed model to create a supported
  widget
- **THEN** the authoring flow advances through the versioned pipeline and
  captures one typed artifact from each required stage
- **AND** accepted JavaScript fragments remain model-generated
- **AND** the application, not the model, assigns identity, revision, digest,
  effective grants, hard resource limits, assembly order, and consent.

#### Scenario: Authoring model is unavailable

- **WHEN** no installed selected model is explicitly verified for widget
  authorship
- **THEN** creation and prompt-based editing are unavailable with a clear
  explanation
- **AND** listing, inspecting, enabling, disabling, refreshing, and deleting
  existing widgets remain available.

### Requirement: Validate feasibility and a typed algorithm before code

The first stage SHALL return one of `achievable`, `unachievable`, or
`needs_clarification` together with bounded display/schedule intent, requested
registered tool versions, runtime values, presentation capabilities, and a
controlled explanation. For an achievable request, a later stage SHALL return a
bounded typed acyclic algorithm whose steps and dependencies distinguish
runtime inputs, independent planned tool calls, transformations, and the final
presentation objective. The application SHALL validate both artifacts before
requesting JavaScript.

#### Scenario: Accept a feasible capability plan

- **WHEN** the model selects only registered `WIDGET`-eligible tool versions and
  supported runtime/presentation capabilities for an achievable request
- **THEN** the application freezes that validated capability envelope for every
  later stage and repair
- **AND** later artifacts cannot broaden it.

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

#### Scenario: Reject unsupported adaptive execution

- **WHEN** the proposed algorithm requires a later tool call whose arguments
  depend on an earlier live provider result
- **THEN** the application rejects it as unsupported by the v1 plan-all runtime
- **AND** does not approximate, reorder, or silently remove the dependency.

### Requirement: Generate and validate dynamic JavaScript by responsibility

For every validated planned tool call, the model SHALL generate one bounded
named JavaScript function that receives only the deterministic runtime stdlib
snapshot and declared inputs and returns one semantic call for the fixed
registered tool/version. Separate stages SHALL generate a short `plan`
orchestrator and a `render` function. The application SHALL syntax-check and
execute each fragment with synthetic normal/boundary fixtures, validate tool
arguments and presentation output against current contracts, and accept no
fragment that uses undeclared inputs or capabilities.

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

#### Scenario: Show multi-round progress

- **WHEN** an authoring attempt advances between stages
- **THEN** the UI updates among feasibility, tool selection, algorithm design,
  call generation N/M, orchestration, presentation, validation, repair, and
  draft-ready states
- **AND** does not imply that an intermediate artifact is saved or executable.

#### Scenario: Cancel during a later stage

- **WHEN** the user cancels or navigates away during any generation, repair, or
  validation stage
- **THEN** active work and ephemeral conversations are released, late callbacks
  are discarded, and all intermediate artifacts are cleared
- **AND** no widget or revision is changed.

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
execution logs, diagnostics, reports, analytics, backups, or exports, and tool
credentials MUST NOT enter model context or stage fields.

#### Scenario: Finish an authoring attempt

- **WHEN** an attempt succeeds, fails, or is cancelled
- **THEN** every ephemeral stage conversation and in-memory artifact is released
- **AND** only a user-confirmed normalized program revision and consent record
  may become durable.
