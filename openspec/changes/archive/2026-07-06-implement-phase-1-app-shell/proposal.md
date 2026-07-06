# Implement Phase 1 App Shell

## Why

ArarAI has its product foundation defined, but the repository still has no
Android application scaffold or executable slice. The next useful step is to
create a small, testable app shell that locks in the build system, Compose app
structure, configured-model startup flow, and inference boundary before native
llama.cpp work adds JNI/NDK complexity.

## What Changes

- Create the Android project scaffold for `com.jesjobom.ararai` with the
  approved SDK, Kotlin, Compose, Gradle, and NDK/CMake baseline.
- Add a single-screen Compose debug chat shell.
- Add checked-in configuration for one fixed GGUF model, including download URL,
  expected local file name/path, integrity metadata, and runtime defaults.
- Add model resolution and download orchestration boundaries that check the
  standard app-owned location before scheduling a download.
- Add a fake `LocalLlmEngine` implementation for deterministic UI/ViewModel
  tests and early app flow validation.
- Add focused tests for model config parsing, model resolution state,
  ViewModel/chat state, and engine event handling.
- Add a debug APK handoff task that copies the built APK to
  `/home/node/.openclaw/jarvis/artifacts/ararai/app-debug.apk`.

## Out Of Scope

- Native llama.cpp build, JNI bindings, tokenization, or real GGUF inference.
- Multiple model choices, model catalog sync, or user-facing model picker.
- Conversation history persistence.
- Voice, image, release signing, or polished settings.
- Automatic ADB install from the OpenClaw container.
