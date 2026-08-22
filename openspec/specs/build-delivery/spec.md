# build-delivery Specification

## Purpose
TBD - created by archiving change harden-build-supply-chain. Update Purpose after archive.

## Requirements

### Requirement: Forward-compatible Gradle configuration

The build SHALL configure every project without invoking APIs scheduled for
removal in Gradle 10.

#### Scenario: Configure the build with full warnings

- **WHEN** a maintainer runs `./gradlew help --warning-mode all`
- **THEN** project and plugin configuration completes successfully
- **AND** no Gradle deprecation warning is emitted

### Requirement: Locked dependency resolution

The build SHALL check in Gradle lock state for every resolvable project
configuration used by application, test, lint, and build workflows.

#### Scenario: Resolve dependencies normally

- **WHEN** Gradle resolves a locked configuration without update flags
- **THEN** selected module versions match the checked-in lock state
- **AND** an unreviewed resolution change fails rather than silently drifting

### Requirement: Verified downloaded artifacts

The build SHALL use strict Gradle dependency verification with checked-in SHA-256
metadata and SHALL pin the Gradle wrapper distribution to its official SHA-256.

#### Scenario: Downloaded artifact differs from reviewed metadata

- **WHEN** Gradle receives an artifact whose checksum is absent or different
- **THEN** dependency resolution fails in strict verification mode

#### Scenario: Wrapper distribution differs from official checksum

- **WHEN** the wrapper downloads a Gradle distribution with a different SHA-256
- **THEN** wrapper bootstrap fails before executing the build

### Requirement: Intentional dependency updates

The repository SHALL document commands and review expectations for changing
versions, lockfiles, verification metadata, and the wrapper checksum.

#### Scenario: Maintainer updates a dependency

- **WHEN** a dependency or plugin version changes intentionally
- **THEN** the maintainer regenerates and reviews locks and verification metadata
- **AND** reruns the complete quality gate before accepting the change

### Requirement: Android builds use a reproducible Java runtime

The repository SHALL declare one supported Java runtime for the canonical
Android Gradle quality gate and SHALL keep workflow configuration, workflow
labels, local prerequisites, and project documentation aligned with that choice.

#### Scenario: CI runs the Android quality gate

- **WHEN** the canonical Android workflow configures Java and invokes Gradle
- **THEN** it uses the same Java runtime declared for local Android builds

#### Scenario: Firebase tooling needs another runtime

- **WHEN** Firebase emulator tooling requires a different Java version
- **THEN** that invocation is isolated and documented without changing the Android Gradle baseline implicitly

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
