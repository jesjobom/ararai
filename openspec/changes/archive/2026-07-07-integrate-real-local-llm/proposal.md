# Integrate Real Local LLM

## Why

The app now has a chat screen backed by the fake `LocalLlmEngine`. The next
product risk is whether the same chat flow can load and use a real GGUF model
that is already present at the configured app-owned path.

This change replaces the chat runtime behind the existing engine boundary with a
real local inference implementation while keeping the scope narrow: no new model
download work, no model picker, and no conversation persistence.

## What Changes

- Add a real `LocalLlmEngine` implementation backed by native GGUF inference.
- Add the Android native build wiring needed for the runtime bridge.
- Load the configured validated model file that already exists on the device.
- Keep chat submission disabled until the configured model is available and the
  runtime is ready for a request.
- Stream generated tokens into the existing chat conversation UI.
- Surface model load, generation, cancellation, and native-runtime failures in
  the chat UI without crashing the app.
- Cancel active generation and unload/release native resources when leaving the
  chat screen.
- Keep the fake engine available for deterministic JVM tests and non-native
  contract coverage.

## Out Of Scope

- Downloading a new model or changing the existing download behavior.
- Model picker, multiple model support, remote catalog, or user-provided model
  files.
- Conversation history persistence.
- Voice, image, attachments, settings, prompt templates, or system-prompt UI.
- GPU acceleration, benchmark tuning, token/sampling controls in the UI, or
  broad performance optimization beyond basic responsiveness and resource
  cleanup.
