# Add LiteRT-LM Gemma Runtime

## Why

Gemma 4 and smaller GGUF variants can produce invalid logits when fully offloaded
through the llama.cpp GPU path on the tested Android device. CPU fallback keeps
the app usable, but it misses the main performance target for Gemma 4.

LiteRT-LM provides Android GPU execution for Gemma 4 `.litertlm` artifacts. The
catalog already has runtime metadata, so this change can add a second runtime
without replacing llama.cpp or changing chat and benchmark UI flows.

## What Changes

- Add the LiteRT-LM Android dependency and GPU native-library manifest entries.
- Add a LiteRT-LM `LocalLlmEngine` implementation using `.litertlm` artifacts.
- Route configured `litert_lm` models to LiteRT-LM while keeping llama.cpp as
  the default for GGUF.
- Add a configured Gemma 4 E4B LiteRT-LM model entry with GPU-preferred
  acceleration.
- Keep the existing Gemma 4 GGUF entry as CPU-only fallback.

## Impact

- Requires downloading a separate Gemma 4 `.litertlm` artifact.
- APK size and dependency graph increase because the app now packages LiteRT-LM.
- LiteRT-LM generation is initially single-turn per request; conversation memory
  remains managed by the existing app-level chat state.
