# Change: Clarify controller lifecycle and architectural boundaries

## Why

`ChatViewModel` owns asynchronous work without an explicit disposal contract while
the Compose root creates it with `remember`. The application root also constructs
most controllers inline, and the LiteRT adapter mixes inference integration with
generic retained-resource ownership and conversation-reuse policy. These hidden
ownership boundaries make navigation and future refactors unnecessarily risky.

## What Changes

- Give `ChatViewModel` an idempotent lifecycle boundary that cancels all owned work.
- Dispose application-scoped controllers and the local runtime when their Compose
  owner leaves composition.
- Extract controller construction from `ArarAiApp` into a dedicated composition
  root while preserving shared session/runtime ownership.
- Extract generic LiteRT resource ownership and conversation reuse policy from the
  Android adapter into focused, independently tested components.
- Preserve all user-visible Chat, Voice Chat, diagnostics, and inference behavior.

## Impact

- Affected specs: `local-llm-hub`
- Affected code: Chat controller lifecycle, Compose application assembly, LiteRT
  resource/reuse internals, and their unit tests.

