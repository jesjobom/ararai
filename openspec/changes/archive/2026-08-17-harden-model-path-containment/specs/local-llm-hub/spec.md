## ADDED Requirements

### Requirement: Application-owned model files remain contained

The application SHALL accept model catalog paths only when they are normalized
relative paths below the application-owned `models/` directory and SHALL verify
canonical containment again before any model file read, write, promotion,
migration, or deletion.

#### Scenario: Valid nested model path

- **WHEN** a catalog entry uses a normalized nested path below `models/`
- **THEN** the application resolves and manages that model within the owned directory

#### Scenario: Malformed path attempts to escape

- **WHEN** a catalog path is absolute, contains traversal, is non-normalized, or resolves outside the owned directory
- **THEN** the application rejects it before performing a filesystem side effect
