# Design: Configuration-aware llama.cpp reuse

## Context

`LlamaCppLocalLlmEngine` retains one native handle. The current compatibility
check compares model ID and path, then updates only `maxTokens`. Context size and
sampling values are passed to the native load call, while Diagnostics deliberately
uses stable benchmark parameters and shares the engine with Chat.

## Decisions

Represent the complete load-bound state in the retained engine state and compare
it before reuse. A compatible load may update only parameters proven to be read
at generation time. An incompatible load unloads the old handle before loading
the requested configuration.

Keep compatibility logic explicit and unit-testable. Do not solve this by always
reloading, because model loading is expensive on mobile and compatible reuse is
an intentional product behavior.

## Validation

- Unit tests cover identical configuration reuse, max-token-only reuse, and
  reloads for every native load-bound parameter.
- Benchmark tests demonstrate that stable benchmark parameters reach the bridge
  after a prior Chat load.
- The project quality gate and physical-device smoke test remain required.
