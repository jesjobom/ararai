## 1. Spec

- [x] Create OpenSpec proposal for configured model runtimes.

## 2. Catalog Schema

- [x] Add runtime, artifact format, and acceleration fields to model types.
- [x] Parse the new catalog fields with backwards-compatible defaults.
- [x] Update the built-in model catalog with explicit runtime metadata.
- [x] Add parser coverage for configured runtime metadata.

## 3. Runtime Wiring

- [x] Drive llama.cpp GPU/CPU policy from catalog acceleration metadata.
- [x] Remove hardcoded CPU-only model IDs from the engine.
- [x] Add a runtime-dispatching local engine boundary.
- [x] Show selected runtime details in benchmark metadata.
- [x] Keep unsupported runtimes fail-fast until their engines exist.

## 4. Validation

- [x] Run `openspec validate support-configured-model-runtimes --strict`.
- [x] Run `./gradlew testDebugUnitTest`.
- [x] Run `./gradlew assembleDebug`.
- [x] Copy the debug APK to
      `/home/node/.openclaw/jarvis/artifacts/ararai/app-debug.apk`.
