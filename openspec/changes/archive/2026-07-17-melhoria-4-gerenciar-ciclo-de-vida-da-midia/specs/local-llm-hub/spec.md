## ADDED Requirements

### Requirement: Owned Chat Media Lifecycle

The app SHALL manage app-owned Chat media according to explicit draft and
persisted-message ownership and SHALL remove files after they become unreferenced.

#### Scenario: Remove a draft attachment

- **GIVEN** an app-owned image or audio file is attached only to the current draft
- **WHEN** the user removes or replaces that attachment
- **THEN** the app removes the unreferenced draft file
- **AND** no persisted message is changed.

#### Scenario: Delete a session containing media

- **GIVEN** a Chat session references app-owned media files
- **WHEN** the session is deleted
- **THEN** its messages are removed atomically according to the session-store contract
- **AND** media with no remaining references is deleted from Chat media storage.

#### Scenario: Preserve referenced media

- **GIVEN** a media file remains referenced by a persisted message
- **WHEN** cleanup or reconciliation runs
- **THEN** the file is preserved.

#### Scenario: Reconcile orphaned Chat media

- **GIVEN** an app-owned Chat media file has no draft or persisted-message reference
- **WHEN** bounded media reconciliation runs
- **THEN** the orphan is removed
- **AND** cleanup does not access files outside the canonical Chat media directory.
