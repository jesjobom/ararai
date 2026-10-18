# Spec Delta

## ADDED Requirements

### Requirement: Notification actions preserve current authoring foreground ownership

A widget-authoring notification action SHALL change only the authoring session
revision it names. Delivery of a stale or duplicate action SHALL NOT end
foreground execution or remove the ongoing notification for a different active
attempt. Foreground execution SHALL end only when the latest attempt has a
committed non-active outcome.

#### Scenario: Stale cancel arrives during a newer attempt

- **GIVEN** a queued, deferred, or running attempt is active at a newer session
  revision
- **WHEN** a Cancel action from an older revision is delivered
- **THEN** the active attempt and its foreground notification remain intact
- **AND** the current attempt remains cancellable from the UI or notification.

#### Scenario: Stale continue or retry arrives during a newer attempt

- **GIVEN** a queued, deferred, or running attempt is active at a newer session
  revision
- **WHEN** a Continue or Retry action from an older revision is delivered
- **THEN** it starts no generation and changes no checkpoint
- **AND** it does not stop foreground execution for the current attempt.

#### Scenario: Matching cancel completes

- **GIVEN** the notification names the current active attempt
- **WHEN** the user requests Cancel
- **THEN** cancellation is requested for that attempt only
- **AND** the foreground notification remains until the cancellation outcome
  is committed
- **AND** foreground execution then ends and the durable retry/review state is
  presented according to the existing authoring workflow.

#### Scenario: Delayed action follows a newer foreground start

- **GIVEN** a newer attempt has requested foreground execution
- **WHEN** an older action is delivered after that start request
- **THEN** the service acknowledges any pending foreground-start contract
- **AND** evaluates its lifetime using the latest authoring state rather than
  stopping solely because the action is stale.
