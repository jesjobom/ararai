# Support Configured Model Runtimes

## Why

ArarAI currently treats every catalog entry as a GGUF model executed through
llama.cpp. That makes it hard to test runtime-specific artifacts, such as a
future LiteRT-LM Gemma bundle, and forces runtime workarounds into engine code
instead of the catalog.

The model catalog should declare which runtime, artifact format, and
acceleration policy each model uses so downloads, validation, engine selection,
and benchmark labeling all follow the same source of truth.

## What Changes

- Extend model catalog entries with runtime metadata.
- Preserve defaults for existing single-model and GGUF catalog configs.
- Move model acceleration policy out of hardcoded engine model IDs.
- Surface selected runtime information in benchmark details.
- Keep current configured models on llama.cpp/GGUF while preparing the schema
  for future LiteRT-LM entries.

## Out of Scope

- Implementing the LiteRT-LM native engine.
- Downloading multi-file model bundles.
- Migrating existing downloaded files on disk.
- Runtime selection UI.
