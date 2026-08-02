## Why

ArarAI's Wikipedia tool is useful for stable encyclopedic lookups, but its
introductory extracts are a poor fit for recent, comparative, ambiguous, or
multi-source questions. Physical E4B testing also showed that large or repeated
tool responses can consume the local model's limited context and leave no
usable final answer, so a general web-search tool must return compact,
query-focused evidence rather than raw pages.

## What Changes

- Keep Wikipedia as a bounded encyclopedic lookup rather than treating it as a
  general research mechanism.
- Add experimental Tavily and Exa web-search providers behind the existing
  application-owned knowledge-tool boundary.
- Add disabled-by-default provider controls to the Tools tab. Enabling either
  provider requires a user-supplied API token and explicit disclosure that the
  query, selected URLs, and provider-required metadata leave the device.
- Store user-supplied tokens as private secrets and exclude them from logs,
  diagnostics, backups, exports, conversation history, and model context.
- Return a common, token-budgeted evidence contract containing short relevant
  excerpts and source metadata, regardless of provider-specific response shape.
- Add deterministic provider tests, direct opt-in smoke tests, and a repeatable
  physical-device comparison across Tavily and Exa using the same bilingual
  question set.
- Keep both providers experimental until the comparison establishes acceptable
  answer quality, source relevance, payload size, latency, reliability, cost,
  and E2B/E4B final-answer completion.
- When both providers are configured and enabled, use Exa first and Tavily as a
  controlled fallback, based on the direct comparison result.
- Restrict Wikipedia's model-visible instructions to direct, stable,
  encyclopedic lookups rather than broad research.
- Defer autonomous deep research, crawling, SearXNG hosting, and DuckDuckGo
  scraping.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `local-llm-hub`: Add user-credentialed experimental Tavily and Exa search,
  compact cross-provider evidence, provider controls and diagnostics, and a
  gated comparison before either provider is approved for ordinary Chat.
- `voice-chat`: Define availability and safe failure behavior for experimental
  web search during the uninterrupted voice loop without exposing credentials
  or speaking tool protocol.

## Impact

- Tools UI and preferences: provider enablement, token entry/removal, privacy
  disclosure, compatibility status, and provider smoke tests.
- Secret storage: Android-private credential persistence with explicit
  backup/export/logging exclusions.
- Knowledge boundary: provider-neutral focused-evidence request/result models
  plus Tavily and Exa HTTP implementations.
- LiteRT-LM boundary: one stable structured web-search tool backed by an
  ordered provider chain, bounded calls, lifecycle events, cancellation, and
  source capture.
- Network/privacy: direct HTTPS requests from the user's device to a provider
  selected and credentialed by that user.
- Validation: fake transports, malformed/error/rate-limit behavior, credential
  handling, bilingual golden queries, physical E2B/E4B runs, and a documented
  provider-selection verdict.
