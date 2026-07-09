## 1. Benchmark Definition

- [x] Define stable benchmark prompt and generation parameters.
- [x] Add a benchmark controller/view model that records load time, first token
      latency, generated token count, generation time, and tokens per second.
- [x] Keep benchmark runnable only when the selected model is locally available.

## 2. UI Integration

- [x] Add a home/menu button for the benchmark screen.
- [x] Add a dedicated benchmark screen with selected model details, stable
      parameters, backend label, run status, and latest result.
- [x] Keep benchmark navigation separate from chat and model management.

## 3. Tests

- [x] Add JVM tests for successful benchmark measurement.
- [x] Add JVM tests for unavailable-model and generation-failure states.

## 4. Validation

- [x] Run `./gradlew testDebugUnitTest`.
- [x] Run `./gradlew assembleDebug`.
- [x] Run `openspec validate benchmark-mobile-inference-runtime --strict`.
- [x] Copy the debug APK to the OpenClaw artifact handoff path.
