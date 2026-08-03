## ADDED Requirements

### Requirement: Application language selection

The application SHALL expose a language selection in General Settings, SHALL
persist the selection locally, and SHALL apply it when creating the application
UI. The initial choices SHALL be the device language, English, and Brazilian
Portuguese. Unknown or missing stored values SHALL fall back to the device
language. The application MAY require a restart before a changed selection is
applied.

#### Scenario: User selects a language

- **WHEN** the user chooses a supported application language
- **THEN** the application persists that choice locally
- **AND** General Settings immediately marks the new choice as selected
- **AND** the application presents a restart-required notice with an action to
  recreate the application UI
- **AND** the next application start presents the interface in that language

#### Scenario: Stored language is unavailable

- **WHEN** no language has been selected or the stored value is unknown
- **THEN** the application follows the device language
- **AND** Android's default resources provide the fallback for unsupported
  device locales

### Requirement: Root back navigation exit confirmation

When Android back navigation is invoked from the application Home destination,
the application SHALL ask the user to confirm closing the application task. The
dialog SHALL allow the user to persist a "do not ask again" choice. Android Home
navigation SHALL retain its platform behavior and only move the application to
the background.

#### Scenario: User confirms exit

- **WHEN** the user invokes Android back navigation from application Home
- **THEN** a localized exit confirmation is presented
- **AND** confirming finishes and removes the application task

#### Scenario: User declines exit

- **WHEN** the exit confirmation is visible
- **AND** the user cancels or dismisses it
- **THEN** the application remains on its Home destination

#### Scenario: User disables future confirmations

- **WHEN** the user confirms exit with "do not ask again" selected
- **THEN** that preference is persisted locally
- **AND** later Android back navigation from application Home closes the task
  without presenting the confirmation

### Requirement: Complete localized interface

All user-visible interface copy, including navigation, settings, Chat, Voice
Chat, model management, diagnostics, dialogs, errors, notifications, empty
states, and accessibility descriptions, SHALL resolve from localized Android
resources for every explicitly supported application language.

#### Scenario: Interface is presented in a selected language

- **WHEN** the application starts with English or Brazilian Portuguese selected
- **THEN** every user-visible interface string is presented in that language
- **AND** user content, model output, model names, and technical identifiers are
  not translated
