## ADDED Requirements

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
