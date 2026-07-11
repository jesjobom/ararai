# Enable GPU Inference By Default

## Why

Physical-device benchmarks showed that CPU-only local inference is not usable
for the larger configured models. Keeping CPU as the default path would make the
main chat and benchmark flows validate the wrong runtime behavior.

ArarAI should prefer GPU acceleration automatically, without adding a user-facing
setting or menu item for the MVP.

## What Changes

- Build llama.cpp with the Android Vulkan backend enabled.
- Load configured GGUF models with GPU layer offload enabled by default.
- Keep the existing `LocalLlmEngine` boundary and chat UI unchanged.
- Update benchmark/backend labeling so measurements identify the GPU-default
  runtime.
- Fall back gracefully if native GPU initialization cannot load the model, while
  keeping GPU as the first attempted path.

## Out Of Scope

- A settings screen or toggle for CPU/GPU selection.
- Per-device GPU tuning UI.
- Benchmark history persistence.
- Replacing llama.cpp with another runtime.
