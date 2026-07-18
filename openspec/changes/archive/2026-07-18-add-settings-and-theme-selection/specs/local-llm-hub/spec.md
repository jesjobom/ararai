## ADDED Requirements

### Requirement: Application Settings Destination

The app SHALL provide a dedicated Settings destination for application-level
preferences and SHALL organize those preferences into named sections so more
settings can be added without changing the top-level navigation model.

#### Scenario: Open application settings

- **GIVEN** the user is on Home
- **WHEN** the user opens Settings
- **THEN** the app displays the Settings destination
- **AND** application appearance options are grouped under Appearance.

#### Scenario: Return from application settings

- **GIVEN** the user is viewing Settings
- **WHEN** the user navigates back
- **THEN** the app returns to Home.

### Requirement: Application Theme Preference

The app SHALL let the user choose System, Light, or Dark appearance behavior,
apply the choice to the entire application immediately, and persist the choice
locally across application restarts. System SHALL resolve to the current Android
system appearance. Missing or unrecognized stored values SHALL resolve to
System.

#### Scenario: Select an explicit theme

- **WHEN** the user selects Light or Dark in Settings
- **THEN** the whole application immediately uses the corresponding appearance
- **AND** the selection is restored on a later application launch.

#### Scenario: Follow system appearance

- **WHEN** the user selects System in Settings
- **THEN** the application uses the Android system light or dark appearance
- **AND** follows later system appearance changes.

#### Scenario: Retain dynamic color behavior

- **GIVEN** Material dynamic colors are available on the device
- **WHEN** a theme preference resolves to light or dark
- **THEN** the application uses the corresponding dynamic color scheme.
