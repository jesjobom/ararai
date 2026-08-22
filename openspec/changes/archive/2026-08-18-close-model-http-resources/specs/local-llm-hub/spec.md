## ADDED Requirements

### Requirement: Model downloads own network resources explicitly

The application SHALL deterministically close response resources and disconnect
the underlying model-download connection after success, HTTP failure, I/O
failure, cancellation, and each failed fallback attempt.

#### Scenario: Download succeeds

- **WHEN** a model response is consumed and validated
- **THEN** its stream and connection are released after promotion completes

#### Scenario: Download attempt terminates early

- **WHEN** a response fails, is rejected, or is cancelled
- **THEN** all resources owned by that attempt are released before returning or trying another URL
