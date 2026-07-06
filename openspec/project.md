# Project Context

## Purpose

ArarAI is an Android hub for local open LLMs. It should let users obtain or
select supported models, send prompts, and receive streamed responses while all
inference runs on the device.

The project is intentionally exploratory: it should grow through small validated
increments, test device constraints early, and preserve the ability to replace
runtime implementations later.

## Product Decisions

- The product name is `ArarAI`.
- The Android namespace and application ID start as `com.jesjobom.ararai`.
- The MVP targets Android SDK 36 and does not need to support old Android
  versions.
- The MVP uses no external application backend, remote database, or hosted API.
- The first model source is a user-selected local GGUF file through Android's
  file picker. During early testing, model files can be copied to the physical
  device manually and selected from local storage.
- Android release signing is out of scope initially; debug builds are enough.
- Fase 0 model feasibility testing is skipped because representative local
  models have already been tested in other Android applications.

## Initial Stack

- Kotlin 2.3.21
- Jetpack Compose
- Jetpack Compose BOM 2026.06.00
- Compose Compiler Gradle plugin matching Kotlin 2.3.21
- Gradle wrapper
- Android Gradle Plugin 9.2.x initially, with AGP 8.13.2 as a fallback only if
  the first Compose plus NDK scaffold hits real compatibility friction
- Gradle 9.4.1
- JDK 17 for the build runtime
- Android SDK 36
- Android Build Tools 36.0.0
- Android NDK
- Android NDK 28.2.13676358
- Android SDK CMake 3.22.1
- CMake and Ninja
- llama.cpp through JNI/NDK
- GGUF model files
- Room or plain SQLite only when local persistence becomes necessary
- DataStore for small preferences
- WorkManager for model downloads when download flow is introduced
- Android TextToSpeech and SpeechRecognizer for early voice experiments

## Architecture Direction

The app should isolate model execution behind an engine boundary so the first
implementation can use llama.cpp while preserving room for future runtimes.

Candidate boundary:

```kotlin
interface LocalLlmEngine {
    suspend fun load(model: LocalModel, config: InferenceConfig)
    fun generate(request: PromptRequest): Flow<GenerationEvent>
    suspend fun unload()
}
```

The UI, model catalog, prompt/session state, media handling, and inference
runtime should remain separate enough that runtime experiments do not rewrite
the whole app.

## First Implementation Slice

The first vertical slice is a single-screen debug chat flow:

1. Launch the app.
2. Select a local GGUF model file through the Android file picker.
3. Load the selected model through the `LocalLlmEngine` boundary.
4. Submit one text prompt.
5. Stream generated text back into the chat view.
6. Unload the model when leaving the screen or replacing the model.

This slice excludes conversation history, remote downloads, model catalog sync,
voice, image input/output, release signing, and polished settings. It should
still include automated tests around prompt/session state, engine-boundary
events, and failure handling before native runtime work is wired in.

## Development Process

ArarAI should be developed with TDD by default. For each behavior where an
automated test is practical, create or update a failing test first, verify the
failure, implement the smallest change that makes it pass, and then refactor
without changing behavior.

Automated tests should be preferred for domain logic, state management,
ViewModels, model catalog behavior, persistence, download orchestration, and
runtime boundary contracts. Manual device checks are still required for local
LLM performance, JNI/NDK integration, Android permissions, thermal behavior, and
end-to-end UX, but they do not replace automated tests for testable code.

If a slice cannot reasonably start with an automated failing test, document the
reason in the change notes or task checklist and add the closest practical
guardrail before considering the slice done.

## Test Environment

The OpenClaw container is expected to be used for source edits and APK builds.
It is not expected to run an Android emulator reliably.

The expected early test split is:

1. Build APK inside the OpenClaw container.
2. Copy the generated debug APK to
   `/home/node/.openclaw/jarvis/artifacts/ararai/app-debug.apk`, outside the
   Git-tracked project files.
3. Install and run it on the physical Android device using ADB outside the
   container.
4. Inspect failures through device logs and iterate.

This means feedback loops will be slower than a fully local Android Studio
setup. The project should compensate with small slices and clear manual test
checklists.

## Open Questions

- Whether to use Room from the beginning or defer persistence until history or a
  local catalog exists.
- Whether remote device logs can be pulled back into OpenClaw automatically.
