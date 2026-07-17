## ADDED Requirements

### Requirement: Atomic Chat Message Append

The Chat session store SHALL persist a new message and update its owning
session timestamp as one atomic operation.

#### Scenario: Append a message successfully

- **GIVEN** the target Chat session exists
- **WHEN** a message is appended
- **THEN** the message and updated session timestamp commit together
- **AND** session ordering reflects the appended message.

#### Scenario: Append to a missing session

- **GIVEN** the target Chat session does not exist
- **WHEN** a message append is attempted
- **THEN** the store reports a controlled persistence failure
- **AND** no orphan message is committed.

#### Scenario: Fail while updating the session

- **GIVEN** message insertion begins inside a transaction
- **WHEN** the owning session cannot be updated exactly once
- **THEN** the transaction rolls back
- **AND** neither partial message state nor a partial timestamp change remains.
