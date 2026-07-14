# Stabilize llama.cpp Sampling

## Why

Some GGUF models can fall into degenerate output loops on-device, especially
when sampling is configured with only temperature and top-p. The observed Qwen
behavior produced repeated characters and unusable responses after moving to
CPU-only execution.

The app should use a more defensive default sampler chain for llama.cpp models
and keep per-model generation settings tuned enough to avoid obvious repetition
failure modes.

## What Changes

- Add top-k, min-p, and repeat penalty samplers to the native llama.cpp sampler
  chain.
- Tune Qwen GGUF catalog entries to a smaller default max output and Qwen-style
  temperature/top-p values.
- Keep the existing CPU-only Qwen policy until GPU/Vulkan correctness is proven
  for those files.

## Impact

- llama.cpp output should be less likely to enter simple repetition loops.
- Qwen remains slower on CPU-only execution.
- If a Qwen GGUF still produces nonsensical output after sampler hardening, the
  model variant should be replaced rather than patched further in the runtime.
