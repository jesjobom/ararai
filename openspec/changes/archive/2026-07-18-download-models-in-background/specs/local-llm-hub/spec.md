## ADDED Requirements

### Requirement: Background model download

The app SHALL run active model downloads as user-visible foreground data
transfers so they can continue after the application UI moves to the background.
The current transfer state SHALL remain observable when the activity is
recreated.

#### Scenario: Continue a download in background

- **WHEN** a model download is active and the user leaves ArarAI
- **THEN** the transfer continues under a foreground service
- **AND** a system notification reports the model and download progress.

#### Scenario: Cancel from notification

- **GIVEN** a model download notification is visible
- **WHEN** the user selects Cancel
- **THEN** the transfer stops
- **AND** the model returns to its applicable non-downloading state.

#### Scenario: Cancel from model management

- **GIVEN** a model download is active
- **WHEN** the user selects Cancel in the model-management screen
- **THEN** the stream copy stops without waiting for the remote response to
  finish
- **AND** the foreground notification is removed after cancellation completes.

#### Scenario: Open download from notification

- **GIVEN** a model download notification is visible
- **WHEN** the user taps the notification
- **THEN** ArarAI opens or returns to the model-management screen.

#### Scenario: Open the notification repeatedly

- **GIVEN** ArarAI already has an application task in the background
- **WHEN** the user taps the download notification one or more times
- **THEN** the existing task opens the model-management screen
- **AND** no duplicate application activity is added to the Back stack.

#### Scenario: Report sustained progress

- **WHEN** an active model download reports new byte progress
- **THEN** the notification periodically reflects the latest known percentage
- **AND** updates are paced to avoid overwhelming Android's notification system.

### Requirement: Resumable partial model transfer

The app SHALL preserve partial model bytes after cancellation or transient
failure and SHALL request the remaining HTTP range on a later attempt. It SHALL
append only when the server confirms the requested range and SHALL otherwise
restart the temporary file from zero. Integrity validation and atomic promotion
SHALL remain required before a model becomes available.

#### Scenario: Server accepts resume

- **GIVEN** a valid partial model file exists
- **WHEN** the server accepts a request beginning at the partial byte count
- **THEN** the app appends the remaining bytes
- **AND** validates and atomically promotes the completed model.

#### Scenario: Server ignores resume

- **GIVEN** a partial model file exists
- **WHEN** the server returns the complete artifact instead of the requested
  range
- **THEN** the app truncates the partial file before writing
- **AND** does not duplicate bytes.

### Requirement: Background download notification permission

On Android versions with runtime notification permission, the app SHALL request
permission in the context of an active model download. Permission denial SHALL
NOT be represented as a guarantee that Android can run the transfer invisibly
or indefinitely.

#### Scenario: Notification permission is not granted

- **WHEN** a model download becomes active on a version requiring runtime
  notification permission
- **THEN** the app requests that permission
- **AND** denial does not crash or immediately fail the model transfer.
