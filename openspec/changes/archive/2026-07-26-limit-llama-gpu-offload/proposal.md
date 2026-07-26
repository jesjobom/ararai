## Why

The checked-in Llama 3.2 3B configuration currently maps GPU-preferred
acceleration to `n_gpu_layers=999`, which effectively requests full model
offload. Device diagnostics show that this consumes about 2.64 GB of Adreno
memory, drives the process above 5 GB PSS, blocks Android rendering behind GPU
fences, and can produce an input-dispatch ANR during longer conversations.

The application needs a conservative, explicit llama.cpp offload budget so GPU
acceleration remains available without treating all available GPU memory as an
application-owned resource.

## What Changes

- Add an optional per-model llama.cpp GPU-layer count to catalog metadata.
- Replace the unbounded `999` default with a conservative eight-layer default
  for legacy GPU-preferred GGUF entries.
- Preserve finite per-model offload support, but configure the checked-in Llama
  3.2 3B entry as experimental CPU-only after eight-layer physical validation
  still produced GPU-fence ANRs and invalid logits.
- Add experimental CPU-only LFM2.5 1.2B and Ministral 3 3B text profiles for
  comparative physical testing without re-entering the failed Vulkan path.
- Preserve CPU-only behavior and the existing CPU fallback when native GPU
  initialization or zero-token generation fails.
- Treat GPU-layer count as load-bound runtime state so changing it reloads the
  native model.
- Document that physical-device profiling is still required before increasing
  a checked-in offload budget.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `local-llm-hub`: Bound llama.cpp GPU offload using explicit catalog metadata
  and a conservative compatibility default instead of unlimited layer offload.

## Impact

- Model catalog parsing, validation, resolution, and runtime metadata.
- llama.cpp engine load compatibility and native `n_gpu_layers` input.
- Static model catalog entries and integrity metadata for LFM2.5 and Ministral.
- Unit tests for safe defaults, explicit budgets, CPU-only validation, and
  reload behavior.
- README/project documentation and the checked-in Llama 3.2 configuration.
