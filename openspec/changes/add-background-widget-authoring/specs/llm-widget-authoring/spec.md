## Purpose

Coordinate where and when local widget authorship runs so a requested
generation continues as an application-scoped background job with visible
state, cancellation, single-flight enforcement, and device-state deferral,
without changing the authoring pipeline stages or confirmation flow.

## ADDED Requirements

### Requirement: Application-scoped authoring jobs

The application SHALL run a requested widget authoring generation as an
application-scoped job that continues while the user navigates within the app
and survives leaving the authoring screen, as long as the process lives. The
job SHALL use the shared application-scoped local LLM engine and SHALL NOT
create a second concurrent engine instance.

#### Scenario: Generation continues after leaving the screen

- **WHEN** the user submits a generation request and navigates away from the
  authoring screen without cancelling
- **THEN** the job keeps running at application scope
- **AND** returning to the authoring screen shows the same job state.

#### Scenario: Process death is reported, not silently ignored

- **WHEN** the process dies while an authoring job is in flight
- **THEN** no partial draft or widget state is persisted from the interrupted
  generation
- **AND** a new identical request may be submitted after restart.

### Requirement: Single-flight authoring

The application SHALL allow at most one active widget authoring job at a time.
While a job is queued, running, or deferred, new generation requests SHALL be
refused with a user-visible explanation instead of being silently dropped.

#### Scenario: Second request refused while a job is active

- **WHEN** the user requests another widget generation while a job is queued
  or running
- **THEN** the request is refused with an explanation that one generation is
  already in progress
- **AND** the active job is unaffected.

### Requirement: Cancelable background authoring

The user SHALL be able to cancel the active authoring job from the authoring
UI or from the job notification at any point before completion. Cancelling
SHALL stop the generation, release pipeline state, and transition the job to
a cancelled state that permits a new request.

#### Scenario: Cancel from the notification

- **WHEN** the user taps the cancel action on the authoring notification
- **THEN** the active generation stops
- **AND** the job state becomes cancelled
- **AND** a new generation request is accepted.

### Requirement: Device-state deferral

The application SHALL evaluate device state (thermal status, available
memory, and system low-memory signal) before starting a requested generation
and SHALL defer the job when the state is unacceptable instead of forcing
generation under pressure. Deferred jobs SHALL retry with a bounded attempt
budget and SHALL transition to an explicit failed state with a stated reason
when the budget is exhausted.

#### Scenario: Defer under severe thermal status

- **WHEN** a job starts while the thermal status is severe or worse
- **THEN** the job is deferred with a stated reason visible in the UI and
  notification
- **AND** a retry is scheduled for later
- **AND** no generation stage runs while the unacceptable state persists.

#### Scenario: Exhausted retry budget fails explicitly

- **WHEN** the deferral retry budget is exhausted without an acceptable
  device state
- **THEN** the job transitions to failed with a deferral-exhausted reason
- **AND** the UI and notification reflect the failure.

### Requirement: Visible authoring job state

The application SHALL expose the current authoring job state (queued, running
stage, deferred reason, succeeded, failed, cancelled) in the authoring UI and,
while a job is active, in a persistent foreground-service notification that
includes a cancel action. The notification state SHALL match the in-app job
state.

#### Scenario: Notification matches in-app state

- **WHEN** the job state changes while the job runs
- **THEN** the persistent notification reflects the new stage or deferral
  reason
- **AND** it is removed when the job reaches a terminal state, with the final
  outcome still visible in the app.