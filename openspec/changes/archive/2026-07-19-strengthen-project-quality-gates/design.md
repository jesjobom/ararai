# Design: Deterministic Kotlin and CI quality feedback

## Context

The common gate runs tests, Android lint, builds, and strict OpenSpec validation.
It has no Kotlin formatter or Kotlin-focused analyzer. CI caches Gradle but
installs SDK/NDK/CMake and fetches pinned native sources on each clean runner.
The canonical consolidated spec declares its Purpose as TBD.

## Decisions

Adopt one pinned formatter (ktlint directly or through Spotless) and one pinned
Kotlin-aware analyzer (detekt). Configure checks first with a reviewed baseline;
keep mechanical formatting separate from behavioral changes. Add check-only tasks
to `scripts/quality-gate.sh` and document local fix commands separately.

Cache only reproducible SDK/native and FetchContent directories. Keys include OS,
tool versions, lock/config files, and CMake inputs; restore keys must not allow
incompatible native artifacts to masquerade as current outputs. Validate cold and
warm runs and retain Gradle's existing cache.

Replace the spec Purpose with a concise description of the current local LLM hub,
runtime boundaries, app-owned data, and physical-device validation constraints.
Do not alter requirements while correcting this metadata.

## Validation

- Deliberate formatter/analyzer fixtures prove each gate fails and passes correctly.
- CI evidence compares cold and warm runs and confirms correct cache invalidation.
- Strict OpenSpec validation and the full project quality gate pass.
