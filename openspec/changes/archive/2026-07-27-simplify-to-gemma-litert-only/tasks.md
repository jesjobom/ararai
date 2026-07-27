## 1. Baseline and Contract

- [x] 1.1 Record the current debug APK size and packaged llama.cpp native
  library size using reproducible commands.
- [x] 1.2 Add or update catalog/parser tests for Gemma E2B default, Gemma-only
  chat entries, preserved Whisper entries, and rejected removed runtimes.

## 2. Catalog and Upgrade Migration

- [x] 2.1 Remove all GGUF chat entries and make Gemma 4 E2B the checked-in
  default.
- [x] 2.2 Implement idempotent exact-path cleanup for former managed GGUF files
  and partial downloads while preserving unknown, Gemma, and Whisper files.
- [x] 2.3 Wire the legacy-artifact migration into application startup and cover
  it with automated tests.

## 3. Runtime Simplification

- [x] 3.1 Wire LiteRT-LM directly as the application's `LocalLlmEngine` and
  remove multi-runtime dispatch.
- [x] 3.2 Remove llama.cpp Kotlin/JNI/C++ sources and GGUF-specific runtime
  tests.
- [x] 3.3 Remove the app CMake/NDK/Vulkan/llama.cpp build integration while
  preserving Whisper's independent native module.
- [x] 3.4 Remove llama.cpp/GGUF enum values, GPU-layer configuration, benchmark
  branches, and obsolete tests.

## 4. Documentation and Validation

- [x] 4.1 Update README, project context, device-validation guidance, and any
  affected active OpenSpec wording to describe Gemma-only chat inference.
- [x] 4.2 Run targeted tests during implementation and the complete
  `scripts/quality-gate.sh` after the change.
- [x] 4.3 Build the post-change debug APK, compare its size with the baseline,
  copy the artifact, and record physical-device checks still required.
- [x] 4.4 Validate the OpenSpec change strictly and mark every completed task.
