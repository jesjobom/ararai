## MODIFIED Requirements

### Requirement: Select refresh cadence in a dedicated authoring stage

For new schema-version-3 authoring sessions, achievable feasibility SHALL advance to a distinct schedule stage before algorithm generation. Only that stage SHALL receive the seven new-choice intervals of 1, 2, 4, 6, 8, 12, and 24 hours; an editing session MAY also retain its current interval. It SHALL also offer no-periodic-refresh and return exactly one permitted choice. It SHALL select the closest offered cadence when a requested cadence is not exactly supported, without introducing a frequency-specific clarification outcome. The accepted schedule artifact SHALL be authoritative for the draft; feasibility SHALL neither request nor return a periodic interval. Existing schema-version-1 sessions SHALL retain their original stage path and schedule meaning; schema-version-2 sessions SHALL retain the separate schedule stage and their four-field feasibility artifact with a null interval.

#### Scenario: Four updates per day

- **WHEN** a user requests four updates per day
- **THEN** feasibility omits the schedule field and the dedicated stage selects the offered 6-hour interval
- **AND** the draft uses the accepted schedule-stage interval.

#### Scenario: Three updates per day

- **WHEN** a user requests three updates per day
- **THEN** feasibility omits the schedule field and the dedicated stage accepts the offered 8-hour interval
- **AND** the draft uses the accepted schedule-stage interval.

#### Scenario: Closest supported cadence

- **WHEN** a requested periodic cadence is not exactly supported
- **THEN** the stage chooses the closest supported option instead of asking for clarification about the frequency
- **AND** the application rejects values outside the offered set.

#### Scenario: Legacy in-progress session

- **WHEN** a schema-version-1 session resumes after upgrading the app
- **THEN** it retains its prior feasibility-to-algorithm transition and persisted feasibility schedule.

#### Scenario: In-progress session with the earlier dedicated schedule contract

- **WHEN** a schema-version-2 session resumes after upgrading the app
- **THEN** its feasibility contract still requires a null periodic interval field
- **AND** it retains the separate schedule stage without discarding accepted checkpoints.
