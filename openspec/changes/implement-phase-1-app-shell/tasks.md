# Tasks

## Project Scaffold

- [x] Create Gradle wrapper and Android settings.
- [x] Create the Android app module with namespace/application ID
      `com.jesjobom.ararai`.
- [x] Configure SDK 36, Kotlin 2.3.21, Compose BOM 2026.06.00, AGP 9.2.x with
      AGP 8.13.2 fallback only if needed, JDK 17, NDK 28.2.13676358, and CMake
      3.22.1.
- [x] Add debug build configuration and repository-local build documentation.

## Model Startup Flow

- [x] Add checked-in fixed model configuration.
- [x] Implement config parsing and validation.
- [x] Implement app-owned model path resolution.
- [x] Implement model availability and integrity checks.
- [x] Implement download orchestration boundary and state reporting.
- [x] Add tests for config parsing, missing file, valid file, invalid file, and
      download-needed states.

## Chat App Shell

- [x] Define `LocalLlmEngine`, `LocalModel`, `InferenceConfig`,
      `PromptRequest`, and `GenerationEvent` contracts.
- [x] Implement a fake streaming engine for tests/debug flow.
- [x] Implement ViewModel/state reducer for model status, prompt input,
      streaming output, and errors.
- [x] Implement the single-screen Compose debug chat UI.
- [x] Add tests for prompt submission, streaming events, completion, failure,
      and disabled states while the model is unavailable.

## Build And Handoff

- [x] Add a repeatable debug APK build command.
- [x] Add a task or script that copies the debug APK to
      `/home/node/.openclaw/jarvis/artifacts/ararai/app-debug.apk`.
- [x] Run targeted tests.
- [x] Run the debug APK build.
- [x] Document the manual device validation checklist.
