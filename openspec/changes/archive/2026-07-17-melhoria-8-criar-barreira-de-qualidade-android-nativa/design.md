## Context

ArarAI combines Kotlin/Compose, Android permissions and content providers,
SQLite/filesystem state, a llama.cpp JNI library, and LiteRT-LM vendor/runtime
behavior. JVM tests cover most deterministic logic, while generic CI cannot
truthfully validate arm64 vendor GPU drivers, production-model memory, or thermal
behavior.

## Goals / Non-Goals

- Make the deterministic build/test/spec gate identical locally and in CI.
- Compile instrumentation on every change and run it on a supported arm64 target.
- Smoke-test JNI loading without bundling or downloading a production model.
- Version exact physical-device checks and result metadata.
- Never publish private prompts, media, databases, models, or device logs by default.
- Do not claim GPU, memory, or thermal validation from generic CI.

## Decisions

### One quality-gate entry point

`scripts/quality-gate.sh` runs JVM tests, lint, debug app assembly,
instrumentation APK assembly, and strict validation of all OpenSpec changes.
GitHub Actions calls that script rather than duplicating the command contract.

### CI scope and artifacts

CI installs the pinned Android/NDK/CMake and OpenSpec versions required by the
repository. It uploads synthetic test/lint reports even on failure and app/test
APKs only on success, with seven-day retention. No runtime app data is collected.

### Instrumentation boundaries

The device suite verifies manifest configuration, uses a test-only provider
through the real `ContentResolver`, drives MainActivity through stop/resume, and
loads the packaged JNI library before calling null-handle-safe native symbols.
This is bounded, deterministic, and requires no model artifact.

### Physical-device matrix

GPU backend selection, production-model inference, cancellation under load,
repeated runs, lifecycle, memory pressure, and thermal trends remain a documented
arm64 physical-device gate. Every result records app, device, model, runtime,
acceleration, and explicit skipped checks using synthetic content.

## Risks / Trade-offs

- Native assembly fetches pinned upstream sources and is slower than JVM-only CI,
  but packaging regressions are a defining product risk and remain required.
- Instrumentation is compiled but not executed on a generic x86 CI runner because
  the application packages arm64 native code. A future arm64 device farm can run
  the same `connectedDebugAndroidTest` contract.
- MainActivity lifecycle instrumentation may start the configured bootstrap
  download on a clean device; the test does not wait for or retain that artifact.
