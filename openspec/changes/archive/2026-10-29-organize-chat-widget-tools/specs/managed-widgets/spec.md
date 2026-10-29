## MODIFIED Requirements

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

## ADDED Requirements

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
