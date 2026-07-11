## 1. Specification

- [x] Define LiteRT-LM runtime expectations for configured Gemma 4 models.

## 2. Runtime Integration

- [x] Add LiteRT-LM Android dependency and Android GPU manifest entries.
- [x] Implement LiteRT-LM engine behind `LocalLlmEngine`.
- [x] Route `litert_lm` configured models to the LiteRT-LM engine.

## 3. Catalog

- [x] Add Gemma 4 E4B `.litertlm` catalog entry with runtime metadata and
  integrity metadata.
- [x] Keep existing Gemma 4 GGUF entry as llama.cpp CPU-only fallback.

## 4. Validation

- [x] Add/update JVM tests for runtime routing and LiteRT-LM engine boundaries.
- [x] Run JVM tests.
- [x] Build debug APK and copy handoff artifact if build succeeds.
