# Change: Strengthen project quality gates and canonical metadata

## Why

The shared gate lacks Kotlin formatting/static analysis, CI repeatedly downloads
heavy Android/native toolchains and fetched sources, and the canonical spec still
starts with a placeholder Purpose. These gaps increase review variability,
feedback time, and onboarding ambiguity.

## What Changes

- Add pinned Kotlin formatting and static-analysis checks with a controlled baseline.
- Add versioned CI caches for Android/native tooling and CMake FetchContent inputs.
- Replace the canonical specification placeholder with the implemented product purpose.
- Document the new checks, cache invalidation, and local commands.

## Impact

- Affected spec: `local-llm-hub`
- Affected code: Gradle/static-analysis configuration, quality-gate script, CI workflow, docs
- Runtime application behavior: unchanged
