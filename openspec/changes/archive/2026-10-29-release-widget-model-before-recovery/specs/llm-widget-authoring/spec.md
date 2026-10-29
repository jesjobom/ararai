## ADDED Requirements

### Requirement: Release a retained shared model before the authoring recovery gate

Before sampling the device-state recovery gate for a model-authored stage or retry, the application SHALL unload any model session retained by the shared local engine. If unloading fails, the attempt SHALL end with a controlled retryable runtime failure without loading or generating. Deterministic stages and invalid local stage requests SHALL not cause an unnecessary model transition.

#### Scenario: Retained model allocation before a retry

- **WHEN** a user starts or retries a model-authored stage while the shared engine retains a prior model session
- **THEN** the application unloads that session before evaluating process memory
- **AND** it loads the authoring model only after the recovery gate reports a stable window.

#### Scenario: Retained session cannot be released

- **WHEN** the shared engine fails to unload before the recovery gate
- **THEN** the stage ends with a controlled runtime failure
- **AND** no model load or generation occurs.
