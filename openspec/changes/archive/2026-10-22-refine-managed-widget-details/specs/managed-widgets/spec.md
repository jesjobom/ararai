## ADDED Requirements

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
