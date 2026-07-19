## ADDED Requirements

### Requirement: Configuration-aware llama.cpp runtime reuse

The llama.cpp runtime SHALL reuse a loaded native model only when the requested
model and every inference parameter bound during native loading are compatible
with the retained handle.

#### Scenario: Reuse an identical native configuration

- **GIVEN** a llama.cpp model is loaded with a complete inference configuration
- **WHEN** the same model and compatible configuration are requested again
- **THEN** the app reuses the loaded native handle.

#### Scenario: Reload after a load-bound parameter changes

- **GIVEN** a llama.cpp model is already loaded
- **WHEN** the same model is requested with a different load-bound context or
  sampling parameter
- **THEN** the app unloads the incompatible handle
- **AND** loads the requested configuration before generation or benchmarking.
