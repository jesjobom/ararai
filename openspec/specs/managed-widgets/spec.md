# managed-widgets Specification

## Purpose
Define the local user-facing lifecycle for in-app widgets, including revisioned
storage, consented scheduling, model-independent execution, constrained
rendering, observations, diagnostics, and complete deletion.

## Requirements

### Requirement: Dedicated in-app widget management

The application SHALL expose a Widgets destination from Home where users can
view empty state, list managed widgets and their current status, create a
widget, inspect its active revision and permissions, enable or disable it,
request an immediate refresh, edit it, duplicate it, and delete it. Managed
widgets SHALL be rendered inside ArarAI and SHALL NOT be represented as Android
launcher/app widgets.

#### Scenario: Open an empty manager

- **WHEN** the user opens Widgets before creating any widget
- **THEN** the application shows an empty state and an action to start
  prompt-based creation.

#### Scenario: Inspect a managed widget

- **WHEN** the user opens an existing widget
- **THEN** the application shows its current presentation or empty state,
  enabled/schedule status, last execution status and time, declared tools and
  capabilities, active revision, and available management actions.

#### Scenario: Observe and cancel multi-round authorship

- **WHEN** a user starts prompt-based creation or editing
- **THEN** the screen identifies the active feasibility, tool-selection,
  algorithm, call-generation N/M, orchestration, presentation, validation,
  repair, or draft-ready stage as it changes
- **AND** exposes cancellation while clearly marking all intermediate work as
  unsaved and non-executable.

### Requirement: Revisioned local widget definitions

Each managed widget SHALL have an application-owned stable identity and a
versioned managed definition separate from its immutable validated program
revisions. The active revision, display metadata, schedule, enablement, consent
record, program package, cached presentation, observations, and run metadata
SHALL remain app-private and local, and an unsupported or corrupt revision MUST
NOT be executed.

#### Scenario: Persist a confirmed first revision

- **WHEN** the user confirms a valid new draft and its disclosed background
  behavior
- **THEN** the application assigns the widget identity, stores an immutable
  first program revision, records the confirmed managed definition, and makes
  that revision active atomically.

#### Scenario: Preserve the previous revision on failed edit

- **WHEN** an edited draft is invalid, incompatible, or not confirmed
- **THEN** no new active revision is installed
- **AND** the previous definition, program, schedule, cached presentation, and
  consent remain unchanged.

#### Scenario: Detect an incompatible active revision

- **WHEN** a stored active revision no longer matches a supported manifest,
  runtime API, tool contract, or granted capability
- **THEN** the application disables its execution with a controlled
  needs-attention status
- **AND** retains the definition so the user can inspect, edit, or delete it.

### Requirement: Explicit schedule and enablement

A managed widget SHALL execute in the background only after the user confirms
an enabled schedule. Periodic schedules SHALL use application-supported
intervals of at least one hour, describe that Android timing is inexact, and be
registered as unique replaceable work for the stable widget identity. Disabling
or deleting a widget SHALL cancel its future work.

#### Scenario: Enable periodic execution

- **WHEN** the user confirms an enabled valid widget with a supported schedule
- **THEN** the application registers exactly one periodic work definition for
  that widget
- **AND** does not promise execution at an exact wall-clock instant.

#### Scenario: Change a schedule

- **WHEN** the user confirms an edit that changes the execution interval
- **THEN** the application replaces the previous periodic work for that widget
- **AND** does not leave both schedules active.

#### Scenario: Reconcile an unchanged schedule after process start

- **WHEN** the application starts with an already registered periodic work
  definition matching the widget revision, interval, and network requirement
- **THEN** reconciliation keeps that work generation and its pending execution
  instead of replacing or interrupting it.

#### Scenario: Network-backed work starts while Android blocks a cached UID

- **WHEN** Android starts a periodic worker whose confirmed tools require network
- **THEN** the work is eligible to start without a JobScheduler connectivity
  constraint that can remain unsatisfied for a cached app UID
- **AND** the worker waits a bounded time for its own UID's network access
  before dispatching a tool
- **AND** if access remains unavailable, it requests bounded WorkManager retry
  without invoking a tool or replacing the last valid presentation.

#### Scenario: Disable a widget

- **WHEN** the user disables a widget
- **THEN** future scheduled work is cancelled
- **AND** no manual or background execution occurs until it is enabled again
- **AND** its stored definition and last valid presentation remain visible.

### Requirement: Model-independent bounded execution

Background and manual widget refreshes SHALL load the active confirmed
revision, validate its current compatibility and grant, run it through the
sandboxed widget runtime, and dispatch only its permitted requests through the
widget tool gateway. Refresh MUST NOT load or prompt a language model, and runs
for the same widget MUST NOT overlap.

#### Scenario: Complete a scheduled refresh

- **WHEN** Android starts work for an enabled compatible widget whose tools are
  ready
- **THEN** one owned run executes the active revision without model inference
- **AND** atomically records its status and any valid presentation and
  observations.

#### Scenario: Coalesce concurrent refresh attempts

- **WHEN** scheduled work and a user refresh target the same widget while a run
  is active
- **THEN** the application permits at most one active execution
- **AND** does not duplicate tool calls or interleave stored results.

#### Scenario: Encounter an unavailable tool

- **WHEN** an active revision requests a tool that is disabled, unconfigured,
  unavailable, or no longer eligible
- **THEN** the run records a controlled failure without asking a model to repair
  it
- **AND** no automatic immediate retry loop is started.

### Requirement: Cached presentation and stale state

The application SHALL cache only a presentation that passed the current
presentation contract and associate it with its program revision and completion
time. A recoverable failed or cancelled refresh SHALL retain the last valid
presentation, visibly mark it stale or failed, and show the failed attempt time;
an invalid presentation SHALL never replace valid cached content.

#### Scenario: Replace the cached presentation

- **WHEN** a run finishes successfully with a valid presentation for the active
  revision
- **THEN** the application atomically replaces the cache and displays its
  retrieval/completion time.

#### Scenario: Preserve content after refresh failure

- **GIVEN** a widget has a last valid cached presentation
- **WHEN** its next run fails, times out, or is cancelled
- **THEN** the application keeps that presentation
- **AND** marks it with the latest controlled stale/error state.

### Requirement: Private bounded observations and execution history

Widgets SHALL access only their own bounded typed observations through granted
history capabilities. Execution history SHALL retain bounded timestamps,
revision, duration, planned tool identities, outcome codes, and sanitized
diagnostics; it MUST NOT retain credentials, provider headers, arbitrary
exception text, raw model protocol, authoring prompts, or data belonging to
another widget. Documented count/age limits SHALL evict oldest entries.

#### Scenario: Read the widget's own history

- **WHEN** a granted program requests an observation nearest a specified prior
  age and tolerance
- **THEN** it receives only a bounded typed match owned by that widget or an
  explicit missing value.

#### Scenario: Inspect execution history

- **WHEN** the user opens a widget's run history
- **THEN** the application displays bounded sanitized outcomes and timestamps
- **AND** does not expose secrets or raw internal stack traces.

#### Scenario: Attempt cross-widget history access

- **WHEN** a program attempts to identify or read another widget's observations
- **THEN** the request is rejected without revealing whether that widget or
  data exists.

### Requirement: Application-owned constrained rendering and actions

The application SHALL render only validated presentation models through fixed
Compose components. Links and other actions SHALL be allowlisted,
user-initiated, and derived from validated structured tool results; widget
source MUST NOT construct Compose code, arbitrary Android intents, WebViews,
HTML, or executable callbacks.

#### Scenario: Render a Wikipedia card

- **WHEN** the active presentation contains supported text, semantic icon/style
  tokens, and a validated canonical Wikipedia link
- **THEN** the application renders them with fixed accessible components
- **AND** opens the HTTPS article only after a user action.

#### Scenario: Reject an unsafe action

- **WHEN** a presentation requests an arbitrary intent, unsupported URI scheme,
  callback, or link not traceable to an allowed result field
- **THEN** the action is omitted or the presentation is rejected
- **AND** nothing external is launched.

### Requirement: Complete controlled deletion

Deleting a widget SHALL require user confirmation and atomically make the
widget unavailable for new execution, cancel its scheduled work, and remove its
definitions, program source and revisions, cached presentation, observations,
and run history. A late in-flight result MUST be discarded and MUST NOT recreate
deleted state.

#### Scenario: Delete a managed widget

- **WHEN** the user confirms deletion
- **THEN** the widget disappears from the manager and its unique work is
  cancelled
- **AND** all app-owned durable data for that widget is removed.

#### Scenario: Receive a late result after deletion

- **WHEN** a cancelled run completes after its widget was deleted
- **THEN** the application discards the result
- **AND** does not recreate the widget, cache, observation, or log.

### Requirement: Direct enablement from the widget list

Each managed widget in the Widgets list SHALL expose an accessible switch
reflecting its persisted enabled state. Changing the switch SHALL use the same
enablement and schedule reconciliation operation as the detail screen. A
pending change SHALL disable further switch input until it completes. If the
change fails, the list SHALL reload authoritative state and show a local error.
Activating the switch SHALL NOT open the widget's detail screen.

#### Scenario: Disable a widget from the list

- **WHEN** the user turns off an enabled widget's list switch
- **THEN** the widget is disabled through the managed enablement controller
- **AND** the switch reflects the confirmed disabled state
- **AND** the widget's detail screen is not opened.

#### Scenario: Enable a widget from the list

- **WHEN** the user turns on a disabled widget's list switch
- **THEN** the existing schedule reconciliation runs for that widget
- **AND** the switch reflects the confirmed enabled state.

#### Scenario: Enablement change fails

- **WHEN** the enablement operation fails
- **THEN** the list reloads its persisted state and shows an error for that widget
- **AND** another toggle can be attempted after the pending operation ends.

### Requirement: Focused managed widget details

The widget detail screen SHALL show the active revision alongside the enablement switch and secondary management actions at the top, the immediate refresh action directly after the saved result and its timestamp, and schedule in its own field. Inexact Android schedule timing SHALL be available through contextual help rather than persistent explanatory text. The source SHALL be selectable, syntax-highlighted, and line-wrapped in a distinct code surface without altering the stored text.

#### Scenario: Inspect and manage a widget

- **WHEN** the user opens an existing widget
- **THEN** the top of the details provides its enablement switch and a menu for edit, duplicate, and delete
- **AND** Refresh now follows the last result and timestamp
- **AND** the revision is shown beside the switch and schedule in its own field, without duplicate enabled or revision metadata.

#### Scenario: Read schedule timing guidance

- **WHEN** the user opens the schedule information control
- **THEN** the interface explains that Android chooses an inexact background execution window.

### Requirement: Compact managed widget history

The widget detail screen SHALL initially show at most the four newest retained execution records. When more records exist, the user SHALL be able to expand the full retained history and collapse it again without changing stored run data.

#### Scenario: Inspect a long history

- **WHEN** a widget has more than four retained runs
- **THEN** its detail screen initially shows the four newest records
- **AND** an expand action reveals all retained records in descending recency.
