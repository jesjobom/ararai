## ADDED Requirements

### Requirement: Efficient persisted media reference lookup

The Chat persistence boundary SHALL enumerate referenced media without issuing
one message query per session or materializing complete Chat histories. Reference
updates SHALL remain transactionally consistent with message mutations.

#### Scenario: Enumerate references across many sessions

- **GIVEN** persisted image and audio messages across multiple sessions
- **WHEN** media cleanup requests all referenced URIs
- **THEN** SQLite returns the references through a bounded query path independent of session count
- **AND** does not construct full message histories.

#### Scenario: Mutate a message with media

- **WHEN** a message containing media is inserted, updated, or deleted
- **THEN** its persisted reference state changes in the same transaction
- **AND** cleanup cannot observe a committed message without its references.
