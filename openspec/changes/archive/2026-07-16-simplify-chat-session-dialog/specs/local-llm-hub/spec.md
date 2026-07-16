## ADDED Requirements

### Requirement: Compact session-dialog actions

The Chat session dialog SHALL display `New` beside the `Chat sessions` title.
The bottom action row SHALL display only `Clear all` followed by `Close`. Each
action SHALL include an icon. The dialog SHALL NOT display a separate bottom
rename action.

#### Scenario: Session dialog is opened

- **WHEN** the user opens the Chat session dialog
- **THEN** `New` is displayed beside the dialog title
- **AND** the bottom action row presents `Clear all` followed by `Close`

### Requirement: Rename a session from its card

Chat SHALL open the rename dialog for the specific session card that the user
presses and holds. Renaming a non-selected session SHALL NOT require selecting
that session first.

#### Scenario: User long-presses a non-selected session

- **WHEN** the user presses and holds a non-selected session card
- **THEN** Chat opens a rename dialog initialized with that session's title
- **AND** confirming updates that session without changing the selected session

### Requirement: Compact active-session indication

The Chat session dialog SHALL indicate the active session through its
differentiated card color and SHALL NOT display an additional `Current` text
label.

#### Scenario: Active session is displayed

- **WHEN** the session dialog lists the active session
- **THEN** its card uses the selected-session color
- **AND** no `Current` label consumes space in the card
