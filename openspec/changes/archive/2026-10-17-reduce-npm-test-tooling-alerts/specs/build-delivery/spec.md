## ADDED Requirements

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
