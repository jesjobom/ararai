# build-delivery Specification

## Purpose
Define the reproducible, verified build and delivery controls that protect the
Android artifacts and their dependency supply chain.

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

### Requirement: Connected instrumentation requires destructive-test consent

The build SHALL treat connected Android instrumentation as destructive because
the Android Gradle runner may uninstall the target package during cleanup and
erase app-private data. Connected test execution SHALL be blocked by default and
require an explicit opt-in intended only for a disposable emulator or device.

#### Scenario: Connected test is requested without destructive-test consent

- **WHEN** a maintainer invokes a `connected*AndroidTest` task without the
  destructive-test opt-in
- **THEN** the task fails before its device-side test action starts
- **AND** the target application is not installed, replaced, or uninstalled by
  that task

#### Scenario: Connected test is explicitly run on a disposable target

- **GIVEN** the selected emulator or device has no application data to retain
- **WHEN** a maintainer supplies the documented destructive-test opt-in and runs
  a `connected*AndroidTest` task
- **THEN** the build permits the Android instrumentation runner to execute
- **AND** the retained-device workflow continues to use build-only and
  non-destructive manual validation paths

### Requirement: CI actions use reviewed immutable revisions

The Android quality workflow SHALL reference each external GitHub Action by a
full commit SHA rather than a mutable version tag. It SHALL retain readable
version context and support reviewable update proposals without adding a
per-release APK generation step.

#### Scenario: CI runs without an action update

- **WHEN** the Android quality workflow runs on a pull request, main push, or manual dispatch
- **THEN** every external action resolves to its reviewed commit SHA
- **AND** generating an APK requires no action-version maintenance step

#### Scenario: An upstream action version changes

- **WHEN** an updated GitHub Action becomes available
- **THEN** an automated update can propose a new pin for review
- **AND** the existing CI pin remains unchanged until the proposal is accepted

### Requirement: Node test tooling remains locked and reviewable

The repository SHALL keep exact top-level versions and a checked-in npm lockfile
for Node-based quality-gate tooling. Non-forced security updates SHALL preserve
the test contract, and unresolved advisories SHALL be recorded with their
development-only exposure and update path.

#### Scenario: Test-tooling dependency is updated

- **WHEN** a maintainer updates an npm dependency used by the quality gate
- **THEN** the lockfile records the complete resolved graph
- **AND** a clean `npm ci --include=dev --ignore-scripts` and the quality gate pass

#### Scenario: An advisory has no compatible upstream fix

- **WHEN** a current direct dependency still contains a vulnerable transitive package
- **THEN** the maintainer records the affected tool and exposure
- **AND** does not force a major downgrade or untested override solely to make the audit count zero
