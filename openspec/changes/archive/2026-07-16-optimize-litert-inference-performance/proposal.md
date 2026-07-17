## Why

The Gemma 4 LiteRT-LM runtime appears substantially slower than the same model
in another Android application, but ArarAI currently reports streamed callback
count as token count and combines prefill latency with decode throughput. The
app needs trustworthy measurements first, followed by focused runtime changes
that avoid unnecessary multimodal initialization and repeated prompt prefill.

## What Changes

- Replace callback-count "tokens/s" with runtime-backed token metrics when the
  LiteRT-LM API exposes them; otherwise report an explicitly named comparable
  fallback metric and never label callback chunks as tokens.
- Measure time to first output and post-first-output throughput separately so
  prompt prefill does not distort decode throughput.
- Configure LiteRT-LM modality backends from the needs of the active workload,
  allowing a text-only profile that does not initialize vision or audio
  processing.
- Reuse a LiteRT-LM conversation for compatible consecutive turns so the
  runtime can retain conversation state and KV cache instead of rebuilding the
  full prompt on every generation.
- Invalidate and recreate the retained conversation whenever its model,
  sampling settings, reasoning mode, modalities, or chat-session identity are
  incompatible, and release it on cancellation/unload as appropriate.
- Provide an app-owned LiteRT-LM cache directory and preserve safe fallback and
  cleanup behavior.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `local-llm-hub`: Make benchmark metrics semantically accurate and make the
  LiteRT-LM runtime workload-aware, cache-enabled, and capable of safe
  conversation reuse.

## Impact

- Affects benchmark state, presentation, and tests.
- Affects the LiteRT-LM engine boundary, conversation lifecycle, runtime
  configuration, and dependency wiring for an app-owned cache directory.
- May require a small runtime-observability extension if LiteRT-LM 0.14 exposes
  usable token or performance statistics; no dependency upgrade is assumed
  without validation.
- Does not change model artifacts, quantization, llama.cpp behavior, or the
  configured 2048-token context limit.
