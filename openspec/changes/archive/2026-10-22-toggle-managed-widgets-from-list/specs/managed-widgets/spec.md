## ADDED Requirements

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
