## ADDED Requirements

### Requirement: Release shrinking is validated and diagnosable

Release artifacts SHALL use optimized code/resource shrinking only with
evidence-backed keep rules, SHALL preserve mapping artifacts for diagnostics,
and SHALL pass release-like smoke checks across reflection, JNI, Firebase,
Compose, persistence, and local model execution boundaries.

#### Scenario: Release artifact is assembled

- **WHEN** the release build runs R8
- **THEN** it completes with maintained keep rules and produces retrievable mapping metadata

#### Scenario: Shrunk artifact is accepted

- **WHEN** a shrunk release-like artifact is evaluated for delivery
- **THEN** critical runtime smoke checks pass on a physical device in addition to automated build checks
