## ADDED Requirements

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
