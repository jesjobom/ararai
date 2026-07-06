# Design Notes

## Phase Boundary

Phase 1 should make the app buildable, testable, and runnable without depending
on native inference yet. The goal is to prove the Android structure and state
flow before JNI/NDK integration becomes the main source of risk.

The phase should still model the real runtime shape:

- model metadata comes from checked-in configuration
- startup resolves the configured model from app-owned storage
- missing or invalid files enter a download-needed/downloading/error state
- chat uses the same `LocalLlmEngine` boundary planned for llama.cpp
- UI consumes streamed generation events from a fake engine in tests and debug
  development

## Model Configuration

The first implementation should keep one model entry in a checked-in asset or
resource file. The config should include:

- stable model ID
- human-readable model name for logs/debug UI
- source URL
- expected file name
- standard relative storage path under app-owned files
- expected byte size when known
- SHA-256 or equivalent integrity metadata
- default inference parameters needed by the engine boundary

Do not introduce a remote catalog. A future change can replace the static
config with a catalog only after the single-model flow works.

## Download Boundary

Use a small model-resolution boundary instead of letting the UI or ViewModel
touch files directly. The boundary should report explicit states such as:

- available
- missing
- integrity failed
- download queued
- downloading with progress when available
- failed with recoverable error details

WorkManager is the preferred implementation mechanism for actual download work,
but the state machine and repository contracts should be testable without
Android background execution.

## Chat Shell

The Compose screen should be a dense debug-first chat surface, not a marketing
or onboarding screen. It should show enough operational state to debug first-run
model resolution:

- model availability/download status
- prompt input
- submit/cancel affordance
- streaming response text
- visible load, download, and generation errors

The fake engine can return deterministic streamed chunks so tests can assert
state transitions before the native runtime exists.

## Validation

Automated validation should run in the container. Manual device validation is
limited to installing the generated debug APK outside the container and checking
that the app launches and renders the debug chat shell.
