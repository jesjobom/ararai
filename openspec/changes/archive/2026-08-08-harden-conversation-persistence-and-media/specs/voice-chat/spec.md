## ADDED Requirements

### Requirement: Atomic Voice Chat media ownership

Voice Chat SHALL append an audio user turn to canonical conversation history only
after its recording has been copied successfully from temporary capture storage
into app-owned Chat media storage.

#### Scenario: Persist a captured audio turn

- **GIVEN** Voice Chat has produced a valid temporary recording
- **WHEN** app-owned Chat media storage accepts the copy
- **THEN** the canonical user message references the app-owned copy
- **AND** normal temporary-capture cleanup does not remove persisted audio.

#### Scenario: App-owned media copy fails

- **GIVEN** Voice Chat has produced a valid temporary recording
- **WHEN** app-owned Chat media storage cannot create or copy the destination
- **THEN** Voice Chat enters a controlled error state
- **AND** deletes the temporary capture
- **AND** does not append a user message or media reference for that turn
- **AND** does not submit generation with the temporary path.
