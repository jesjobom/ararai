## MODIFIED Requirements

### Requirement: Private Provider Credential Handling

The application SHALL treat user-supplied provider tokens as secrets stored in
app-private Android credential storage. Tokens MUST NOT be included in model
context, conversation history, saved UI state, source metadata, logs,
diagnostics, analytics, crash text, backups, or exports. The application SHALL
distinguish an absent credential from unreadable encrypted data, SHALL NOT treat
an unreadable credential as configured or enabled, and SHALL offer a safe path
to replace it without revealing credential contents.

#### Scenario: Restore provider configuration UI

- **GIVEN** a provider token is already stored and readable
- **WHEN** the Tools screen is recreated
- **THEN** it reports that a credential is configured
- **AND** it does not display or repopulate the full token.

#### Scenario: Produce diagnostics after an authenticated request

- **WHEN** a provider request succeeds or fails
- **THEN** diagnostics contain only redacted provider, timing, size, status
  class, and controlled failure information
- **AND** contain neither the token nor an authorization-header value.

#### Scenario: Back up or export application data

- **GIVEN** one or more provider tokens are stored
- **WHEN** Android backup or an ArarAI data export is produced
- **THEN** provider tokens SHALL be excluded.

#### Scenario: Stored credential becomes unreadable

- **WHEN** encrypted provider credential data cannot be decrypted
- **THEN** the provider is unavailable for requests and the user is prompted to save a replacement credential
- **AND** the unreadable ciphertext is retained until explicit replacement or removal

#### Scenario: Valid stored credential remains readable

- **WHEN** encrypted provider credential data decrypts successfully
- **THEN** existing provider enablement and request behavior are preserved
