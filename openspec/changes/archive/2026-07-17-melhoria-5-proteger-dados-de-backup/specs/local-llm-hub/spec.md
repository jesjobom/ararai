## ADDED Requirements

### Requirement: Explicit Local Data Backup Policy

The app SHALL define Android backup and device-transfer behavior explicitly and
SHALL exclude private, large, derived, or reference-sensitive local data from
platform-managed extraction.

#### Scenario: Evaluate cloud backup content

- **GIVEN** Android backup evaluates ArarAI app-owned data
- **WHEN** backup rules are applied
- **THEN** Chat databases, Chat media, downloaded models, temporary downloads, and runtime caches are excluded.

#### Scenario: Evaluate device-to-device transfer

- **GIVEN** Android device transfer evaluates ArarAI app-owned data
- **WHEN** data-extraction rules are applied
- **THEN** excluded private and reference-sensitive data is not transferred
- **AND** the restored app cannot receive dangling Chat media references from platform backup.

#### Scenario: Inspect documented privacy behavior

- **WHEN** a maintainer or user reviews ArarAI data behavior
- **THEN** the documentation states what remains only on-device
- **AND** states whether any limited preference data is eligible for backup.
