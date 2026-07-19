# Change: Reload llama.cpp when inference configuration changes

## Why

The llama.cpp engine currently reuses a loaded model by model identity and path
while refreshing only the maximum output-token limit. Parameters bound during
native model loading can therefore remain stale when Chat and Diagnostics use
the same runtime with different configurations.

## What Changes

- Treat all native load-bound inference parameters as part of llama.cpp runtime
  compatibility.
- Reload the native model when any load-bound parameter changes.
- Preserve the inexpensive reuse path when only request-time parameters change.
- Add regression coverage for sequential loads of the same model with different
  configurations.

## Impact

- Affected spec: `local-llm-hub`
- Affected code: llama.cpp engine state/reuse logic and engine tests
- User data, model files, and LiteRT-LM behavior: unchanged
