## Why

ArarAI has strong routine quality gates, but it does not yet have one explicit,
repeatable workflow for deeper code, architecture, and security reviews. The
shared Semgrep and CodeQL installations are now available through stable
executable paths, making this a good point to organize a measured audit before
starting isolated fixes.

## What Changes

- Establish a read-only baseline and lightweight threat model for the application.
- Run a senior codebase review with `improve` and consolidate its evidence into a
  prioritized finding register.
- Run focused Semgrep scans through the shared, pinned executable and manually
  triage every reported finding.
- Build and assess a fresh CodeQL Java/Kotlin database, evaluate project-specific
  data extensions, and run high-precision followed by broad explicit suites.
- Add manual Android/Firebase and dependency/supply-chain review steps where
  general static analyzers do not provide sufficient coverage.
- Convert confirmed findings into separate, bounded OpenSpec remediation changes,
  using `ponytail` during implementation to favor the smallest sound correction.
- Define evidence, severity, deduplication, validation, and closure criteria so
  tool output is not mistaken for a verified vulnerability or a clean bill of
  health.

## Capabilities

### New Capabilities

None. This change organizes engineering analysis and follow-up work without
changing application behavior or its product contract.

### Modified Capabilities

None.

## Impact

- Affected systems: repository analysis workflow and generated local audit
  artifacts.
- Application/runtime behavior: unchanged.
- Dependencies: no application dependency is added; the workflow uses shared
  Semgrep 1.172.0 and CodeQL 2.26.2 installations outside the repository.
- Delivery: this change produces a reviewed backlog and follow-up OpenSpec
  changes; remediation is intentionally outside its scope.
