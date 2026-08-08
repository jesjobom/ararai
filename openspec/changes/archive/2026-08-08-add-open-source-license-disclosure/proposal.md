# Change: Add open-source license disclosure

## Why

ArarAI now has an Apache 2.0 project license, but users cannot inspect the
licenses and attribution notices for the Gradle libraries, native whisper.cpp
runtime, or downloadable model artifacts distributed or managed by the app.
A manually maintained list of direct dependencies would also omit transitive
components and drift during dependency upgrades.

## What Changes

- Generate the Gradle dependency and license inventory as part of the Android
  build from resolved dependency metadata.
- Add explicit reviewed disclosures for native code and downloadable model
  artifacts that are outside the Gradle graph.
- Expose a localized Open-source licenses destination from Settings.
- Document and validate the update process so dependency changes cannot silently
  leave the disclosure stale.

## Impact

- Affected specs: `local-llm-hub`
- Affected code: Android build configuration, Settings navigation and UI,
  localized strings, license metadata, tests, and release documentation.
- This is compliance support, not a legal opinion; upstream metadata still
  requires review when dependencies or catalog artifacts change.
