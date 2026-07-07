# Tasks

## Proposal

- [x] Create OpenSpec proposal for real local LLM integration using an already
      present configured model file.

## Tests First

- [x] Add or update tests for chat state when the real engine reports model
      loading, generation progress, completion, and failure.
- [x] Add or update tests that generation cannot start while model loading or a
      previous generation is active.
- [x] Add or update tests that leaving chat cancels active work and calls the
      engine unload path.
- [x] Preserve fake-engine tests so deterministic JVM coverage remains available
      without native libraries.

## Native Runtime

- [x] Add Android native build wiring for the local GGUF runtime bridge.
- [x] Add a Kotlin real-engine implementation behind `LocalLlmEngine`.
- [x] Add the JNI/native bridge required to load a GGUF model, generate tokens,
      report errors, and release resources.
- [x] Map configured `InferenceConfig` values into the native runtime defaults.
- [x] Ensure model loading and generation run off the main thread.

## Chat Integration

- [x] Wire chat to the real engine for app runtime while keeping the fake engine
      available for tests.
- [x] Show a clear loading/runtime status before and during first real model
      use.
- [x] Stream generated text into the existing assistant message.
- [x] Disable send while model is unavailable, loading, or generating.
- [x] Surface load/generation errors without clearing existing conversation
      messages.
- [x] Cancel active generation and unload native resources when leaving chat.

## Validation

- [x] Run `./gradlew testDebugUnitTest`.
- [x] Run `./gradlew assembleDebug`.
- [x] Run `openspec validate integrate-real-local-llm --strict`.
- [x] Copy the debug APK to
      `/home/node/.openclaw/jarvis/artifacts/ararai/app-debug.apk`.
- [x] Document the physical-device smoke test result or the exact blocker if it
      cannot be executed from the current environment.

Physical-device smoke test blocker: this OpenClaw container can build the APK
but does not have direct ADB access to the Android device. The APK has been
copied to the handoff path for external device validation.
