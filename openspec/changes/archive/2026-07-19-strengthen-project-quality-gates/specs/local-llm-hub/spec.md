## ADDED Requirements

### Requirement: Kotlin formatting and static-analysis gate

The shared automated quality gate SHALL run pinned, check-only Kotlin formatting
and static-analysis tasks in addition to tests, Android lint, builds, and strict
OpenSpec validation.

#### Scenario: Kotlin source violates an enforced rule

- **WHEN** Kotlin source violates configured formatting or static-analysis policy
- **THEN** the shared local and CI quality gate fails with an actionable diagnostic.

### Requirement: Reproducible native CI caching

CI SHALL reuse compatible Android/native toolchain and fetched-source inputs with
cache keys that invalidate when their pinned versions or defining build inputs change.

#### Scenario: Native build inputs remain compatible

- **WHEN** a CI run restores a cache produced by matching tool and CMake inputs
- **THEN** it reuses those inputs and still performs the required build validation.

#### Scenario: Native build inputs change

- **WHEN** a pinned tool version or defining CMake input changes
- **THEN** CI does not treat incompatible cached native inputs as current.
