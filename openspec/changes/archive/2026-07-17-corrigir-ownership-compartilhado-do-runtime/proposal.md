# Change: Share local runtime ownership between Chat and Benchmark

## Why

Chat and Benchmark currently create independent native inference engines. After a
Chat generation, opening Benchmark can therefore load a second multi-gigabyte
LiteRT-LM session while the Chat session is still retained, causing abrupt
process termination under memory pressure.

## What Changes

- Introduce one application-scoped owner for the configured local LLM engine.
- Inject the same engine instance into Chat and Benchmark.
- Keep Benchmark cleanup authoritative: completing, canceling, or leaving a
  benchmark unloads the shared runtime, and Chat reloads it on its next prompt.
- Add tests that prevent independent native runtime trees from being wired
  again.

## Impact

- Affected spec: `local-llm-hub`
- Affected code: app composition and local inference ownership
- Compatibility: no persisted data, model format, prompt, or UI contract change

