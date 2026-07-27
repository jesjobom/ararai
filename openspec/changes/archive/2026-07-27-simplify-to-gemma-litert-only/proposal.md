## Why

ArarAI currently carries two independent chat inference stacks even though its
product direction is centered on the multimodal Gemma 4 models. Standardizing
chat on Gemma 4 through LiteRT-LM removes a large native dependency and reduces
maintenance and compatibility risk without changing local-only inference.

## What Changes

- **BREAKING** Remove the SmolLM2, Llama 3.2, LFM2.5, and Ministral GGUF models
  from the checked-in catalog and make Gemma 4 E2B the default chat model.
- **BREAKING** Remove llama.cpp/GGUF as a supported chat runtime and artifact
  format.
- Keep Gemma 4 E2B and E4B through LiteRT-LM as the supported chat and reasoning
  models.
- Preserve Whisper models and whisper.cpp for local audio transcription.
- Remove the llama.cpp JNI/C++ implementation, CMake/Vulkan build integration,
  runtime dispatch, GGUF-specific configuration, benchmarks, and tests.
- Simplify documentation and validation around the single supported LLM
  runtime.
- Measure the release-equivalent APK before and after the change; real-model
  performance and compatibility remain physical-device validation items.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `local-llm-hub`: Restrict configured chat models to Gemma 4 LiteRT-LM,
  remove GGUF/llama.cpp support, and make Gemma 4 E2B the default.

## Impact

- Model catalog, parsing types, resolution, model presentation, benchmark
  labels, startup wiring, and tests.
- Removal of the app's native llama.cpp source and external CMake build.
- Removal of llama.cpp, Vulkan shader build, and GGUF-specific configuration.
- Existing downloaded GGUF files become orphaned after upgrade unless explicit
  migration cleanup is provided; this change will define and test that policy.
- Whisper's separate native runtime and transcription catalog remain supported.
