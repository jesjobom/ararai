# Experimental focused web-search providers

## Scope

Wikipedia remains the preferred tool for stable encyclopedic facts such as a
biography, birth date, work, television show, or concise concept summary.
Tavily and Exa are experimental alternatives for current, comparative,
ambiguous, technical, or multi-source questions.

The implementation deliberately does not expose autonomous deep research,
arbitrary browsing, provider-generated answers, or provider-specific schemas to
the local model. A debug-only gate allows the checked-in Gemma 4 E2B/E4B
bundles to exercise one stable `web_search` tool while the providers are
compared. An unapproved provider remains unavailable to release builds.

## User-owned credentials

ArarAI does not contain a Tavily or Exa API token. A user configures their own
token under **Assistant configuration → Tools** and accepts that:

- the provider receives the query, focused extraction request, selected URLs or
  equivalent provider retrieval metadata, IP/network metadata, and the user's
  credential;
- the provider's terms, retention policy, rate limits, quota, and charges
  apply; and
- inference and conversation persistence remain local, but web retrieval does
  not.

The token is encrypted at rest with an AES-GCM key generated in Android
Keystore. The UI reports only configured/not configured and never reads the
full value back. Tokens are excluded from model prompts, conversations, source
metadata, logs, diagnostics, Android backup/device transfer, and application
exports. Removing the credential disables that provider before another turn;
clearing application data or uninstalling also removes it.

This direct device-to-provider design is valid because the credential belongs
to the user. An application-owned paid credential must not be shipped in the
APK and would require a backend or equivalent secret-preserving service.

## Common evidence contract

The local model sees one vendor-neutral schema:

```json
{
  "query": "short search query",
  "language": "en",
  "focus": "the exact fact or comparison to resolve"
}
```

ArarAI binds enabled providers behind that schema in deterministic order. When
both are enabled, Exa runs first and Tavily is the fallback. Changing enablement
changes the effective system instruction and invalidates incompatible retained
native conversation state.

Provider output is normalized as untrusted external reference data:

- maximum three distinct canonical HTTPS sources;
- maximum two excerpts per source;
- maximum 500 characters per excerpt;
- maximum 1,800 characters including source framing;
- whitespace and duplicate normalization;
- Unicode-safe truncation;
- provider, title, canonical URL, language, and retrieval timestamp retained as
  bounded source metadata; and
- maximum two `web_search` calls per user turn.

Tavily generated answers and Exa generated summaries are disabled. Tavily uses
Search followed by query-focused Extract. Exa uses Search with query-focused
Highlights. ArarAI never follows result URLs itself in this increment.

## Network and failure boundaries

Only these fixed HTTPS endpoints are accepted:

- `https://api.tavily.com/search`
- `https://api.tavily.com/extract`
- `https://api.exa.ai/search`

Cleartext, redirects, model-selected endpoints/headers, oversized responses,
invalid UTF-8/JSON, unsupported media types, and malformed sources fail closed.
Authentication, quota, rate-limit, timeout, cancellation, malformed-response,
and provider-unavailable outcomes map to controlled errors. When both providers
are enabled, ArarAI calls the fallback only after the primary returns no
evidence, rejects authentication or quota, is rate limited or unavailable,
times out, or returns a malformed response. It does not fall back after success,
invalid model arguments, or cancellation. Fallback can consume quota from both
services.

Normal Chat and Voice Chat use the same progress, source persistence, and
incomplete-answer semantics. Voice Chat does not speak credentials, tool
protocol, raw excerpts, or URLs, and does not enqueue empty TTS.

## Current validation status

Deterministic tests cover evidence limits, unsafe/duplicate sources, Unicode
boundaries, exact tool arguments, two-call enforcement, response validation,
provider error mapping, cancellation, credential state, diagnostic redaction,
model capability gating, engine event identity, and Compose configuration
flows. An Android instrumentation test verifies encrypted-at-rest credential
round-trip and plaintext absence from shared-preference files.

Live provider smoke tests, the paired bilingual corpus, physical E2B/E4B model
runs, cost measurements, and the provider approval verdict remain required
before release enablement.
