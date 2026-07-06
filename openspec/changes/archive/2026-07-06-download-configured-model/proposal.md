# Download Configured Model

## Why

The app can parse and resolve the fixed model configuration, but a missing or
invalid model currently only produces a disabled debug chat state. The next
small step is to make first-run setup useful by downloading the configured GGUF
model automatically and promoting it to the final app-owned path only after
validation.

## What Changes

- Implement the concrete download path for the single configured GGUF model.
- Download to a temporary file next to the final model file.
- Validate expected byte size and SHA-256 before the file becomes loadable.
- Promote the validated file to the configured final path with an atomic rename
  on the same filesystem.
- Surface simple model download states in the debug UI: missing, downloading,
  available, failed.
- Add a retry action for failed downloads.
- Add automated tests for missing file, invalid file, successful download,
  validation failure, temporary-file cleanup, and retry state.

## Out Of Scope

- Native llama.cpp/JNI inference.
- Multiple models or model picker.
- Remote model catalog.
- Pause/resume support.
- Advanced network policy, metered-network controls, or background-only UX.
- Partial-content resume or multi-part downloads.
