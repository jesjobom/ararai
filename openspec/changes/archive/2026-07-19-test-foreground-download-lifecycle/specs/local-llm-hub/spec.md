## ADDED Requirements

### Requirement: Automated foreground download lifecycle coverage

The project SHALL automatically verify the service ownership and state
transitions that keep foreground model downloads reliable across Android
lifecycle events.

#### Scenario: Redeliver a download command

- **GIVEN** the service receives a redelivered download intent
- **WHEN** it reattaches to application-scoped download state
- **THEN** automated coverage verifies that the transfer remains owned exactly once
- **AND** completion stops the service after no owned transfers remain.

#### Scenario: Destroy a service with owned work

- **GIVEN** the service owns one or more active transfers
- **WHEN** the service is destroyed
- **THEN** automated coverage verifies cancellation for each owned transfer
- **AND** verifies cleanup of observation and ownership state.

#### Scenario: Receive an empty start intent

- **WHEN** Android invokes the service without a valid model command
- **THEN** automated coverage verifies controlled non-sticky behavior without a crash.
