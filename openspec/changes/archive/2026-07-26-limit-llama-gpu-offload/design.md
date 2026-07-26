## Context

`ModelAccelerationPolicy.GpuPreferred` currently collapses two distinct
decisions: whether llama.cpp may use Vulkan and how many transformer layers it
may offload. `LlamaCppLocalLlmEngine` converts that policy to either zero or
`999` layers. The latter is an unlimited sentinel, not a device-safe budget.

The immediate safety correction must be deterministic and testable without
pretending that the container can validate vendor GPU scheduling. The exact
performance optimum remains physical-device work.

## Goals

- Ensure the shipped Llama 3.2 path never requests unlimited GPU offload.
- Keep acceleration configuration model-specific and checked in.
- Preserve backward compatibility for legacy catalogs that only declare
  `gpu_preferred`.
- Reload llama.cpp when the effective layer budget changes.
- Leave LiteRT-LM GPU selection semantics unchanged.

## Non-goals

- Automatic GPU-memory detection or device-specific tuning.
- A user-facing acceleration slider.
- Per-turn performance telemetry.
- KV-cache reuse for llama.cpp.
- Claiming physical stability from JVM/native build tests.

## Decisions

### Catalog field and compatibility default

GGUF entries may declare `gpuLayerCount=<positive integer>` when their
acceleration policy is `gpu_preferred`. The value is copied through
`ModelConfig` and `LocalModel` into the llama.cpp runtime.

A legacy GPU-preferred GGUF entry without the field receives an effective
eight-layer budget in `LlamaCppLocalLlmEngine`. This keeps old catalogs valid
while removing the unlimited sentinel. CPU-only remains exactly zero layers.

### Validation boundary

`gpuLayerCount` is valid only for llama.cpp/GGUF with GPU-preferred
acceleration. Zero or negative values are rejected because CPU-only is already
represented explicitly by the acceleration policy. LiteRT-LM keeps its
backend-level GPU/CPU choice and does not consume llama.cpp layer metadata.

### Checked-in model policy

Physical validation rejected the initial eight-layer Llama 3.2 3B profile: the
target Adreno device still blocked inference and Android rendering on GPU
fences, produced an input-dispatch ANR, and returned an all-zero logits tensor.
The checked-in Llama profile is therefore experimental and CPU-only.

LFM2.5 1.2B Instruct Q4_K_M and Ministral 3 3B Instruct Q4_K_M are added as
experimental CPU-only text profiles for comparative testing. Both use official
GGUF artifacts supported by the pinned llama.cpp architecture set. Ministral's
separate vision projector is deliberately omitted. Re-enabling GPU offload for
any checked-in GGUF model requires new physical evidence covering multi-turn
responsiveness, numerical correctness, memory, ANR, and thermal behavior.

### Runtime compatibility

The effective GPU-layer count remains part of `LoadedState`. Re-loading the
same model with a different count unloads and recreates the native handle.
Existing CPU fallback continues to rebuild with zero layers.

## Risks and mitigations

- CPU-only models may be slower than partial offload: correctness and Android
  responsiveness take precedence; later device measurements can justify a
  checked-in GPU layer count.
- Some devices may still struggle: CPU-only remains supported and physical
  validation remains mandatory.
- A catalog author could configure an aggressive finite count: configuration is
  checked in and reviewed; this change removes accidental unlimited behavior
  but does not invent unreliable cross-device memory heuristics.
