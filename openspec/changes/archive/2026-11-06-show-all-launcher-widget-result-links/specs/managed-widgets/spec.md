## MODIFIED Requirements

### Requirement: Android launcher snapshot

The system widget picker SHALL offer an ArarAI widget. The user SHALL choose an existing managed widget for each launcher instance. The instance SHALL show a bounded projection of that widget's validated saved presentation in an app-owned fixed layout, including an appropriate empty, disabled, stale, or unavailable state. A presentation with multiple validated links SHALL show the links in presentation order as individually clickable rows in a scrollable launcher collection. Tapping the instance outside a link SHALL open that widget's details in ArarAI. Rendering the launcher card SHALL NOT evaluate model-authored code, call tools, or start model inference; only an explicit refresh action may schedule execution of the confirmed program in ArarAI.

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

#### Scenario: Open a saved result from a list

- **WHEN** a saved presentation contains two to ten validated search-result links
- **THEN** each link appears as its own clickable row in presentation order
- **AND** the rows can be scrolled if the launcher card is too small to show them all
- **AND** selecting a row opens only a destination still present in that widget's current validated saved result.

#### Scenario: Refresh from the launcher

- **WHEN** the user taps refresh on an enabled bound launcher instance
- **THEN** the app schedules one coalesced execution of the confirmed widget program
- **AND** a committed result updates the card
- **AND** repeated taps do not create concurrent executions.
