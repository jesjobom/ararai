# Change: Simplify the web-search tool contract

## Why

The local model can emit malformed `web_search` calls when it must generate three
related string arguments. LiteRT-LM rejects those calls before the application
tool runs, so configured search providers never receive the request.

## What Changes

- Expose only one required `query` string in the model-facing `web_search` schema.
- Derive provider-only language and focus metadata inside the application adapter.
- Preserve provider selection, request limits, validation, evidence normalization,
  source capture, and user-visible failure reporting.

## Impact

- Affected spec: `local-llm-hub`
- Affected code: web-search OpenAPI adapter and deterministic unit tests
- Compatibility: internal provider contracts remain unchanged
