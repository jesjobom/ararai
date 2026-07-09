# ArarAI

ArarAI is an Android application concept for running open local LLMs on-device.
The first milestone is a focused text-chat MVP that resolves configured open
models, downloads a small default model automatically when no configured model
is available, runs inference locally, and streams text responses without
depending on a remote API or external database.

## Current Direction

- Product name: ArarAI
- Platform: Android
- Android package/application ID: `com.jesjobom.ararai`
- Target SDK: Android SDK 36
- Runtime direction: llama.cpp with GGUF models through JNI/NDK
- Build toolchain: Kotlin 2.3.21, Compose BOM 2026.06.00, JDK 17, AGP
  9.2.x, Gradle 9.4.1, Build Tools 36.0.0, NDK 28.2.13676358
- First device target: Galaxy 26 physical device
- Backend: none for the MVP
- External database: none for the MVP
- Model access: a static checked-in GGUF model catalog; startup downloads the
  configured small default model only when no configured model is available
  locally
- Android signing: debug builds only for now
- Development process: TDD by default; write a failing test before implementing
  each behavior when an automated test is practical

## Planning

Project decisions and requirements are tracked under `openspec/`.
The project definition lives in:

- `openspec/project.md`
- `openspec/specs/local-llm-hub/spec.md`
- `openspec/changes/implement-phase-1-app-shell/`

## Build

Run local unit tests:

```sh
./gradlew testDebugUnitTest
```

Build the debug APK:

```sh
./gradlew assembleDebug
```

Copy the debug APK to the OpenClaw handoff location:

```sh
scripts/copy-debug-apk.sh
```

## Manual Device Check

After copying the APK to the handoff location, install it from an environment
with ADB access and verify:

- the app launches as `ArarAI`
- the debug chat screen renders without crashing
- configured models are visible
- prompt submission is disabled until a configured model is available
- device logs do not show startup crashes or resource failures
