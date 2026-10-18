# Spec Delta

## ADDED Requirements

### Requirement: Configured model transfers obey their declared size

A configured model download SHALL have a positive expected byte size before
transfer begins. The app SHALL never write more than that size to its partial
artifact, including when resuming, and SHALL discard an oversized partial.
Matching the size SHALL NOT bypass checksum validation or atomic promotion.

#### Scenario: Response exceeds the declared size

- **GIVEN** a configured model declares an expected byte size
- **WHEN** a source sends more than that size, including bytes already present
  in a resumable partial
- **THEN** the transfer stops before writing the excess bytes
- **AND** the oversized candidate is not retained for resume or promoted
- **AND** the download reports a controlled failure or proceeds to a separately
  configured fallback without combining bytes from different sources.

#### Scenario: Existing partial already exceeds the declared size

- **GIVEN** a partial model file is larger than the configured expected size
- **WHEN** a download or retry begins
- **THEN** the app discards that partial before requesting or appending bytes
- **AND** no final model file is changed by that discard.

#### Scenario: Configured size is unavailable

- **GIVEN** a model entry has no positive expected byte size
- **WHEN** the user requests its download
- **THEN** the app refuses transfer before opening a network response
- **AND** reports a controlled configuration failure without altering an
  existing valid model file.

#### Scenario: Valid exact-size download or resume

- **GIVEN** the source delivers exactly the remaining bytes for a configured
  model
- **WHEN** the transfer completes
- **THEN** the app applies the existing byte-size and SHA-256 checks
- **AND** only a validated artifact is atomically promoted.

### Requirement: Active generation retains exclusive native session ownership

The shared local inference runtime SHALL NOT close or replace a native model
session while generation is using it. Load, workload preparation, reload,
recovery, and unload SHALL coordinate with active generation. Cancellation
SHALL release ownership so a waiting transition can complete.

#### Scenario: Unload or reload requested during generation

- **GIVEN** generation is actively using a loaded native session
- **WHEN** another app flow requests unload, reload, or a different workload
- **THEN** that session remains valid until generation ends or is cancelled
- **AND** no replacement session overlaps the active generation.

#### Scenario: Generation is cancelled with a transition waiting

- **GIVEN** a transition waits for an active generation to release the session
- **WHEN** the generation is cancelled
- **THEN** native cancellation and cleanup complete
- **AND** the transition proceeds without retaining an orphaned session or
  waiting indefinitely.

#### Scenario: Recovery begins after a generation

- **GIVEN** the shared runtime has completed or cancelled a generation
- **WHEN** recovery unloads and later reloads the model
- **THEN** its native transitions remain serialized with any subsequent
  generation using that runtime.

### Requirement: Generation streams preserve event integrity

The local inference runtime SHALL deliver generated text, reasoning, tool
state, and terminal events in order. It SHALL report completion only after all
preceding events were delivered. If bounded delivery cannot preserve that
order, it SHALL report a controlled failure rather than successful partial
output. Caller cancellation SHALL NOT be misreported as delivery overflow.

#### Scenario: Consumer is slower than generated output

- **GIVEN** a generation produces more events than the transport can hold at
  once
- **WHEN** a consumer collects slowly
- **THEN** it receives all events in order before completion or receives an
  explicit controlled failure
- **AND** it never receives a successful terminal event for truncated output.

#### Scenario: Native callback cannot enqueue an event

- **GIVEN** a non-suspending model callback cannot deliver an event within the
  bounded queue
- **WHEN** the generation terminates
- **THEN** the app reports a controlled generation failure
- **AND** it does not retain the partial output as a completed conversation or
  advance a widget-authoring stage as successful.

#### Scenario: Caller cancels a stream

- **GIVEN** a caller stops collecting before generation completes
- **WHEN** the runtime cancels model work and closes the stream
- **THEN** it releases the owned session according to the normal cancellation
  policy
- **AND** it does not report an overflow or a successful completion.
