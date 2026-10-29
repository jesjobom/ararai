# managed-widgets Specification

## Purpose
Define the local user-facing lifecycle for in-app widgets, including revisioned
storage, consented scheduling, model-independent execution, constrained
rendering, observations, diagnostics, and complete deletion.

## Requirements

### Requirement: Dedicated in-app widget management

The application SHALL expose a Widgets destination from Home that opens the managed-widget list directly, with creation, empty state, current status, and navigation to detail. The list's top-right app bar SHALL provide an accessible icon action opening widget tools. Users SHALL be able to inspect a widget's active revision and permissions, enable or disable it, request an immediate refresh, edit it, duplicate it, and delete it. Managed widgets SHALL remain app-owned and rendered inside ArarAI; a user MAY also place a bounded snapshot of an existing managed widget on the Android launcher. A launcher action targeting an existing widget SHALL still open that widget's detail directly.

#### Scenario: Open Widgets from Home

- **WHEN** the user opens Widgets from Home
- **THEN** the application shows the managed-widget list with its creation action and a top-right icon for widget tools.

#### Scenario: Open an empty manager

- **WHEN** the user opens Widgets before creating any widget
- **THEN** the application shows an empty state and an action to start prompt-based creation.

#### Scenario: Inspect a managed widget

- **WHEN** the user opens an existing widget
- **THEN** the application shows its current presentation or empty state, enabled/schedule status, last execution status and time, declared tools and capabilities, active revision, and available management actions.

#### Scenario: Open from an Android launcher instance

- **WHEN** the user taps a launcher instance bound to an existing managed widget
- **THEN** the application opens that widget's detail directly.

#### Scenario: Open widget tools from the list

- **WHEN** the user activates the top-right tools icon on the Widgets list
- **THEN** the application opens widget tool management
- **AND** Back returns to the Widgets list.

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

### Requirement: Android launcher snapshot

The system widget picker SHALL offer an ArarAI widget. The user SHALL choose an existing managed widget for each launcher instance. The instance SHALL show a bounded text projection of that widget's validated saved presentation in an app-owned fixed layout, including an appropriate empty, disabled, stale, or unavailable state. Tapping the instance SHALL open that widget's details in ArarAI. Rendering the launcher card SHALL NOT evaluate model-authored code, call tools, or start model inference; only an explicit refresh action may schedule execution of the confirmed program in ArarAI.

#### Scenario: Add a launcher instance

- **WHEN** the user adds the ArarAI widget and selects a managed widget
- **THEN** that Android widget instance is bound to the selected managed widget
- **AND** it shows the most recent valid cached result or a clear empty state.

#### Scenario: Update a saved result

- **WHEN** a manual or scheduled run commits a new valid presentation
- **THEN** every launcher instance bound to that managed widget receives an updated snapshot
- **AND** no model runs as part of launcher rendering.

#### Scenario: Preserve content after a failed refresh

- **WHEN** the latest run fails after an earlier result was saved
- **THEN** the launcher retains that result and marks it stale.

#### Scenario: Disable or delete a managed widget

- **WHEN** the bound managed widget is disabled or deleted
- **THEN** the launcher instance reflects its disabled or unavailable state without executing it.

#### Scenario: Restore a launcher instance

- **WHEN** Android requests a launcher widget update after process death or launcher recreation
- **THEN** the app reconstructs the card from app-private binding and persisted state without requiring network access.

#### Scenario: Read a resized card

- **WHEN** a saved presentation contains a heading, body text, and a valid Wikipedia link
- **THEN** the launcher card distinguishes the heading from body text and wraps the heading
- **AND** a larger card reveals more body text without an arbitrary character or line limit
- **AND** its link appears after the body and opens only an allowlisted destination
- **AND** tapping the card opens the full presentation in ArarAI if the launcher has insufficient space.

#### Scenario: Refresh from the launcher

- **WHEN** the user taps refresh on an enabled bound launcher instance
- **THEN** the app schedules one coalesced execution of the confirmed widget program
- **AND** a committed result updates the card
- **AND** repeated taps do not create concurrent executions.

### Requirement: Widget tool management surface

The Widgets area SHALL show the registered widget tool contracts and their widget-specific eligibility, user enablement, operational readiness, and understandable unavailable reasons. It SHALL distinguish global widget availability from the tools permitted by any individual widget's confirmed revision. A blocked-tool action during widget authoring SHALL open this widget tool surface and preserve a return path to the authoring workflow.

#### Scenario: Review widget tools

- **WHEN** the user opens widget tools
- **THEN** each widget-eligible tool shows its availability and setup state
- **AND** only the newest version of each tool is listed for new widgets; older versions remain executable and available when editing a revision that uses them
- **AND** a tool that is not eligible for widgets is not presented as usable merely because it is configured for Chat.

#### Scenario: Resolve a blocked authoring tool

- **WHEN** widget authoring reports a blocked tool and the user opens tool settings
- **THEN** the application opens widget tool management rather than Chat tools
- **AND** the user can return to the same authoring workflow.

#### Scenario: Enable a widget tool globally

- **WHEN** a user enables a widget-eligible tool in widget tool management
- **THEN** only widget-consumer availability changes
- **AND** existing widget revisions do not gain a tool absent from their confirmed permissions.

### Requirement: Search-only web results for widgets

Managed widget authoring SHALL offer a widget-only `web_search_top_results@1` contract that searches a bounded query through a configured provider and returns up to ten provider-ranked title and validated HTTPS URL results. It SHALL support presentation of the first ranked result or multiple results without fetching site content. Widget enablement SHALL be independent of Chat and default off; a confirmed revision SHALL still explicitly permit the tool. Provider credentials and readiness are shared, but Chat's `web_search@1` behavior SHALL remain unchanged. Refreshes SHALL use the existing inexact schedule and SHALL not load a model.

#### Scenario: First ranked result

- **WHEN** an enabled widget requests one result for a query and refreshes on its one-hour schedule
- **THEN** the tool returns at most the provider's first ranked result with a result-proven link
- **AND** the UI does not claim the result is independently verified.

#### Scenario: Up to ten results

- **WHEN** an enabled widget requests ten results for a query and refreshes on its twelve-hour schedule
- **THEN** the tool returns at most ten bounded ranked result entries
- **AND** neither provider content extraction nor direct site fetching runs.

#### Scenario: Unconfigured or disabled

- **WHEN** no verified provider is configured or widget web search is disabled
- **THEN** widget dispatch returns a controlled failure without a network request
- **AND** Chat's independent availability is unchanged.

### Requirement: Parallel bounded web search modes

Managed widget authoring SHALL offer both the search-only `web_search_top_results@1` contract and a content-bearing `web_search_content@1` contract. The content tool SHALL reuse the configured Chat web-search provider chain to return at most three source-linked results with bounded excerpts, not full page content. The existing Chat contract and top-results behavior SHALL remain unchanged. Both widget tools SHALL require widget web-search enablement, configured credentials, and revision-specific consent.

#### Scenario: Content-bearing result

- **WHEN** a widget using `web_search_content` refreshes with a valid query and a provider returns focused evidence
- **THEN** its tool result contains at most three ranked title, HTTPS URL, and excerpt entries
- **AND** any presented link matches a returned URL and passes the widget destination policy.

#### Scenario: Parallel selection

- **WHEN** a new widget is authored for web search
- **THEN** it can select `web_search_top_results` for up to ten link-only results or `web_search_content` for up to three results with excerpts
- **AND** the Chat-only `web_search` contract is not offered as a widget tool.

#### Scenario: Disabled or unavailable

- **WHEN** widget web search is disabled or no usable credential is configured
- **THEN** neither widget tool sends a provider request
- **AND** existing confirmed revisions retain their exact recorded version and permission.

### Requirement: Consistent widget source review and edit prompt

Before confirming a generated widget, the application SHALL show its JavaScript using the same selectable, syntax-highlighted, line-wrapped code surface as saved-widget detail. Editing a saved widget SHALL prefill the editable instruction field with the active revision's stored prompt, when one exists, without automatically changing the saved widget.

#### Scenario: Review a generated draft

- **WHEN** a valid draft is ready for confirmation
- **THEN** its full JavaScript source is selectable, syntax-highlighted, and line-wrapped in the same code surface used by widget detail.

#### Scenario: Edit an existing widget

- **WHEN** the user selects Edit from a saved widget's detail
- **THEN** the creation/editing screen opens with the active revision's original prompt already in its editable instruction field
- **AND** the user can modify it before starting a new authoring workflow.

#### Scenario: Edit a legacy widget without a saved prompt

- **WHEN** the active revision has no stored prompt
- **THEN** the editable instruction field starts blank.
