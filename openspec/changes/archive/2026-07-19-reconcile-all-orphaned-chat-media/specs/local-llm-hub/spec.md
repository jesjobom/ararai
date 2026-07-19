## ADDED Requirements

### Requirement: Bounded complete Chat media reconciliation

The app SHALL make every unreferenced app-owned Chat media file eligible for
eventual reconciliation even when referenced files sort before it. The configured
limit SHALL bound cleanup candidates, not permanently shield later orphan files.

#### Scenario: Referenced files exceed the cleanup limit

- **GIVEN** more referenced files than the reconciliation limit sort before an orphan
- **WHEN** startup media reconciliation runs
- **THEN** the orphan remains eligible for cleanup
- **AND** referenced media is preserved.

#### Scenario: Reject media outside app ownership

- **WHEN** reconciliation encounters a content URI or path outside the Chat media directory
- **THEN** it does not delete that resource.
