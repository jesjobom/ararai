## Why

The growing catalog mixes language and transcription models in one long list,
while benchmark entry points differ by workload and device suitability is left
for the user to infer from raw RAM requirements. Model management needs a
workload-oriented structure that remains useful as more model families and
variants are added.

## What Changes

- Split Model Management into Chat and Transcription tabs.
- Present model families as contiguous groups and order families and their
  variants from the lightest expected artifact to the heaviest.
- Expose a benchmark action on every downloaded model card and route it to the
  benchmark appropriate for that model's workload.
- Default transcription benchmark execution to the same six CPU threads used
  by normal app transcription while retaining manual benchmark choices.
- Remove the standalone reasoning benchmark/diagnostics entry from Home.
- Read currently available device memory and mark models whose declared free
  RAM requirement fits the device as recommended.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `local-llm-hub`: Organize the configured catalog by workload and family,
  provide per-model benchmark access, and show device-memory-aware
  recommendations.
- `local-audio-transcription`: Reach the transcription benchmark from each
  downloaded transcription model in the shared model manager.

## Impact

- Compose application navigation, Home, model-management tabs, and model cards.
- Catalog metadata and parsing for explicit model-family grouping.
- Device available-memory lookup and pure presentation/sorting policy.
- Compose, parser, and model-presentation tests plus consolidated product
  documentation.
