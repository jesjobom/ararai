## ADDED Requirements

### Requirement: Compact Chat controls during generation

Chat SHALL replace its message composer and auxiliary draft controls with a
compact cancellation action while a response is being generated. If generation
is cancelled or fails, Chat SHALL restore the composer with the submitted draft
content. Successful completion SHALL continue to clear the submitted draft.

#### Scenario: Read a response while it streams

- **WHEN** the model is generating a response
- **THEN** the message field, send action, attachments, and attachment actions
  are not displayed
- **AND** a cancel-generation action remains available.

#### Scenario: Cancel generation

- **GIVEN** the model is generating a response
- **WHEN** the user cancels generation
- **THEN** the message composer is displayed again
- **AND** it contains the submitted draft content.

#### Scenario: Generation fails

- **GIVEN** the model is generating a response
- **WHEN** loading or generation fails
- **THEN** the message composer is displayed again
- **AND** it contains the submitted draft content
- **AND** the failure remains visible.
