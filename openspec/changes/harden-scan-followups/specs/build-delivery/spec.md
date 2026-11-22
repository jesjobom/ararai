## ADDED Requirements

### Requirement: GitHub Actions dependency update cooldown

Dependabot SHALL retain a weekly schedule for GitHub Actions version updates and
apply a seven-day cooldown to newly released versions.

#### Scenario: Process a newly released Action

- **WHEN** Dependabot evaluates a routine version update for an Action released less than seven days ago
- **THEN** it defers the version update
- **AND** the cooldown does not apply to security updates.
