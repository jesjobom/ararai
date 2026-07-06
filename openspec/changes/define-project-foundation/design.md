# Design Notes

## Name

The project name is `ArarAI`.

The name is a good fit because it is short, pronounceable across languages, and
keeps a Brazilian identity through the wordplay with `arara` while still
signaling AI.

## Runtime

The initial runtime should be llama.cpp integrated through JNI/NDK and consuming
GGUF models.

Reasons:

- Large ecosystem of open quantized GGUF models.
- Practical fit for fast Android experiments.
- Good boundary for testing local CPU/GPU behavior before investing in more
  specialized runtimes.
- Does not require external inference services.

The app should not hardwire llama.cpp into UI or feature code. A local inference
engine interface should isolate runtime-specific calls.

## Android Target

The project starts on Android SDK 36. Supporting older Android versions is not a
goal for the MVP.

This keeps the compatibility matrix small and matches the reality that local LLM
inference needs newer hardware anyway.

The Android namespace and application ID start as `com.jesjobom.ararai`.

The initial build toolchain is:

- JDK 17
- Android Gradle Plugin 9.2.x
- Gradle wrapper 9.4.1
- Kotlin 2.3.21
- Jetpack Compose BOM 2026.06.00
- Compose Compiler Gradle plugin 2.3.21
- Android Build Tools 36.0.0
- Android NDK 28.2.13676358
- Android SDK CMake 3.22.1

AGP 8.13.2 remains a fallback if AGP 9.2.x causes concrete friction in the first
Compose plus NDK scaffold.

The Compose setup should use the Compose BOM instead of pinning individual
Compose library versions. The Compose Compiler Gradle plugin should match the
Kotlin plugin version.

## Data And Backend

There is no external database or API for the MVP.

Local persistence should be introduced only when needed:

- preferences through DataStore
- conversation history, local catalog, or model metadata through Room/SQLite

The model catalog should start as a single checked-in configuration entry that
declares the fixed GGUF model URL, expected file name, storage location,
integrity metadata, and runtime defaults. A remote catalog should be introduced
only after the basic local flow is working.

The first model source should be a fixed configured GGUF model. On startup, the
app should check the configured app-owned model path first. If the file is
present and passes the configured integrity check, it can be loaded directly. If
it is absent or invalid, the app should automatically download the configured
model to that standard location. The MVP should not expose a model picker or
model choice yet. Bundling a model in the APK is too heavy for the first loop,
but a single automatic download keeps setup simple while avoiding early catalog
and selection complexity.

## First Slice

The smallest first vertical slice is:

1. Launch the app.
2. Resolve the configured GGUF model from the standard app-owned location.
3. Automatically download the configured model if it is missing or invalid.
4. Load the model through the local inference engine boundary.
5. Send one text prompt.
6. Stream text output into a single chat screen.
7. Surface model resolution, download, load, and generation errors in the UI.
8. Unload the model cleanly.

This intentionally excludes model choice, model catalog sync, conversation
history, Room, voice, image features, release signing, and polished settings.
Before native llama.cpp integration is connected, automated tests should cover
model-resolution and download orchestration, the chat state reducer/ViewModel,
and a fake `LocalLlmEngine` that emits loading, token, completion, and error
events.

## Testing Strategy

Development should follow TDD by default: write the failing automated test for a
behavior first, verify that it fails for the expected reason, implement the
smallest passing change, and then refactor with the test still passing.

This applies strongly to code that can be exercised without a physical device:

- prompt/session state
- model catalog rules
- ViewModels and UI state reducers
- download orchestration
- persistence boundaries
- local inference engine contracts and fake engine behavior

The Android emulator is not part of the main feedback loop because the current
OpenClaw environment is a restricted container and local LLM performance must be
validated on real hardware.

Early validation should use a physical Galaxy 26 device:

1. Build the debug APK in the container.
2. Copy the APK to `/home/node/.openclaw/jarvis/artifacts/ararai/app-debug.apk`
   outside the Git-tracked project files.
3. Run `adb install` outside the container.
4. Test manually on device.
5. Use logs and screenshots to feed failures back into development.

The first implementation slice should be intentionally small:

- app launches
- one prompt can be submitted
- a local model can be loaded
- text response streams back
- UI remains responsive

## Risks

- Slow feedback loop because install and device testing happen outside the
  container.
- JNI/NDK failures are harder to diagnose without direct ADB access in the same
  environment that builds the APK.
- Device performance, thermal behavior, and memory pressure will dominate UX
  quality more than normal Android UI concerns.
- Model file size may complicate storage, network use, retry behavior, and
  first-run latency.
