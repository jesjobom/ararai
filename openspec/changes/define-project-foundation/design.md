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

The initial build toolchain is:

- JDK 17
- Android Gradle Plugin 9.2.x
- Gradle wrapper 9.4.1
- Android Build Tools 36.0.0
- Android NDK 28.2.13676358
- Android SDK CMake 3.22.1

AGP 8.13.2 remains a fallback if AGP 9.2.x causes concrete friction in the first
Compose plus NDK scaffold.

## Data And Backend

There is no external database or API for the MVP.

Local persistence should be introduced only when needed:

- preferences through DataStore
- conversation history, local catalog, or model metadata through Room/SQLite

The model catalog can start as static in-app data or a checked-in JSON file. A
remote catalog should be introduced only when the basic local flow is working.

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
2. Move the APK to the host or machine that has ADB access.
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
- Model packaging and file size may complicate install, storage, and download
  behavior.
