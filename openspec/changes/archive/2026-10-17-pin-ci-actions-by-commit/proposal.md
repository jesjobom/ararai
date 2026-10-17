# Change: Pin CI Actions by Commit

## Why

The Android quality workflow invokes six external GitHub Actions through eleven
mutable major-version tags. Those tags can resolve to different action code on
later runs without a reviewed change in this repository.

## What Changes

- Pin every external action invocation to the full commit SHA currently pointed
  to by its major-version tag, retaining the exact version as a comment.
- Configure weekly Dependabot checks for GitHub Actions updates so future SHA
  changes arrive as reviewable pull requests.
- Document that app releases do not require a separate action-update step.

## Impact

- Affected spec: `build-delivery`
- Affected files: Android CI workflow, Dependabot configuration, quality-gate
  documentation
- App behavior and local APK generation are unchanged.
