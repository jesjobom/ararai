## Context

ArarAI currently exposes one structured external-knowledge provider:
`wikipedia_search`. It calls MediaWiki directly, retrieves up to three
introductory extracts, and returns a mechanically truncated context block.
Physical Gemma 4 E4B testing demonstrated two related constraints:

- encyclopedic search is ineffective for recent or multi-source questions; and
- repeated responses of up to 5,000 characters can consume the model's context
  and reasoning budget before it emits a final answer.

Tavily and Exa both offer web discovery plus query-focused excerpts. Their APIs,
ranking, response schemas, billing, and failure modes differ, so approving one
without a controlled comparison would bake an unverified provider into the
product.

The app is local-first, but an enabled search provider is an explicit network
exception. In this change the API credential belongs to the user, not to the
ArarAI distribution. Direct device-to-provider HTTPS is therefore acceptable
if consent, secret handling, diagnostics, and deletion are explicit.

The Tools tab, application-domain `KnowledgeTool`, LiteRT-LM structured-tool
adapter, source persistence, and Chat/Voice lifecycle already establish the
main integration seams. The active `manage-model-generation` change is expected
to supply configurable context and temperature plus explicit incomplete-answer
handling; this comparison must record those effective values rather than
silently depending on them.

## Goals / Non-Goals

**Goals:**

- Implement experimental Tavily and Exa providers behind one provider-neutral
  focused web-search contract.
- Let a user configure and verify their own token without embedding an
  application-owned secret in the APK.
- Give the local model short, relevant, attributable evidence under a strict
  shared character budget.
- Compare providers fairly with identical tool schemas, prompts, generation
  settings, questions, evidence budgets, and success criteria.
- Preserve Wikipedia as a narrower encyclopedic lookup.
- Produce enough physical-device evidence to approve one provider, reject both,
  or require another experiment.

**Non-Goals:**

- Shipping an ArarAI-managed Tavily or Exa subscription.
- General autonomous browsing, recursive research, page crawling, or a
  server-side research agent.
- SearXNG deployment, DuckDuckGo scraping, or Brave/Jina integration.
- Allowing the model to choose a provider or exposing vendor-specific tools.
- Treating provider-generated summaries as trusted final answers.
- Persisting raw web content, tool protocol, or credentials in conversation
  history.

## Decisions

### Use one stable model-visible `web_search` contract

Tavily and Exa implement a common domain request containing a bounded `query`,
`language`, and `focus`. They return a common result containing provider name,
short evidence excerpts, canonical source metadata, retrieval time, and
controlled failure details.

The enabled providers are bound behind `web_search` in deterministic
Exa-then-Tavily order for a native conversation. The first provider executes first;
the secondary provider is attempted only after a fallback-eligible controlled
failure. Changing enablement invalidates retained conversation state.
The tool description and result envelope remain identical across comparison
runs, preventing the model from seeing vendor-specific schemas or choosing a
provider. Wikipedia remains a separate `wikipedia_search` capability.

Alternatives considered:

- Expose `tavily_search` and `exa_search` simultaneously. This adds tool-choice
  noise, consumes prompt/context capacity, and makes results incomparable.
- Reuse `wikipedia_search`. This hides materially different privacy, freshness,
  provenance, and provider behavior.

### Accept user-owned credentials, but treat them as secrets

Each provider has a separate token field and remove action in the Tools tab.
The token is written only to app-private credential storage protected by
Android platform facilities available to the supported API levels. It is
excluded from Android backup, app data export, diagnostics, analytics, logs,
exceptions, saved state, screenshots where platform secure-window behavior is
practical, model prompts, and conversation persistence.

The UI never reads the full stored value back. It shows only configured/not
configured and, if useful, a non-reversible short label derived without
revealing the token. Replacing or removing a token takes effect before another
request. Uninstalling or clearing app data removes it.

Before activation, the app discloses that the selected provider receives the
query, selected URLs or provider-required retrieval data, IP/network metadata,
and the user's own credential, and that provider terms, retention, quota, and
charges apply. Enabling requires a successful direct smoke test.

A proxy is deliberately not required for this user-owned-token experiment. A
future app-funded subscription would require a backend or another mechanism
that does not distribute the application credential.

### Keep provider credentials and enablement separate

Saving a token does not enable network search. A provider can be:

- unconfigured;
- configured but disabled;
- enabled as preferred provider or fallback; or
- temporarily unavailable because the model/runtime lacks verified tool
  capability.

Both general web providers may be enabled. Exa is always preferred when both
are enabled, based on the direct comparison; Tavily becomes the fallback. The
Tools UI does not expose a priority control and never derives order from
activation history. Provider enablement is independent from Wikipedia
enablement, although comparison runs disable Wikipedia and the competing
provider to isolate the provider under test.

### Fall back only for provider failures that another provider can resolve

The provider chain calls its primary first and attempts the fallback only for
no results, authentication rejection, exhausted quota, rate limiting,
provider/network unavailability, malformed provider response, or timeout.
Invalid model arguments and user cancellation never trigger fallback. A
successful primary response never triggers fallback merely to seek a
subjectively better answer.

Fallback remains inside one model-visible `web_search` invocation, preserves
the global two-invocation-per-turn limit, and returns evidence from only the
provider that succeeded. The model does not see or control provider selection.
The UI discloses that fallback can send the same query and focus to both
enabled providers and can consume quota from both.

### Return extracts, not provider answers

The first experiment disables Tavily `include_answer` and Exa generative
summaries. Provider search identifies candidate URLs; provider-supported
query-focused extraction/highlights select evidence. ArarAI validates and
normalizes that evidence before it enters the model context.

The shared production-candidate budget is:

- at most three distinct HTTPS sources;
- at most two excerpts per source;
- at most 500 characters per excerpt;
- at most 1,800 characters across the complete untrusted reference envelope;
  and
- at most two `web_search` invocations per user turn.

Whitespace is normalized, duplicates are removed, excerpts are truncated on a
Unicode-safe boundary, and title/URL overhead counts toward the total budget.
The exact experiment may include additional lower budgets, but neither provider
gets a larger allowance in a paired run.

Provider scores and generated prose do not become facts. The local model
receives an explicit untrusted-reference frame and must synthesize the answer
with uncertainty where sources do not support a conclusion.

### Keep discovery and extraction provider-native for the comparison

Tavily uses its search and, when needed, query-focused Extract behavior. Exa
uses Search with query-focused Highlights and a maximum-character constraint.
ArarAI does not fetch arbitrary result URLs itself in this increment. This
reduces the app's SSRF and parser surface and tests the main value proposition
of each service: compact agent-oriented evidence.

All provider endpoints, schemes, redirects, status codes, content types,
decoded sizes, source URLs, timeouts, and response structures remain
application validated. The model never supplies an endpoint or arbitrary HTTP
headers.

### Make the comparison paired, repeatable, and decision-oriented

A checked-in corpus contains 20–30 bilingual questions across:

- simple stable facts that Wikipedia should handle;
- recent facts and news;
- comparisons requiring multiple sources;
- ambiguous queries requiring useful ranking;
- technical/product documentation;
- no-result and adversarial/prompt-injection content; and
- questions that should not trigger web search.

Each web-search question is run against Tavily and Exa with a fresh native
conversation, the same model bundle, fixed prompt, context, temperature, tool
schema, call budget, evidence budget, network conditions where controllable,
and run count. Wikipedia is disabled in paired web-provider runs.

The harness records provider calls, HTTP outcome class, source count, evidence
characters and estimated tokens, time to first model token, tool latency, total
latency, reasoning/output token metrics available from LiteRT-LM, final-answer
completion, citations, and answer score. It never records tokens or raw
authorization headers.

Approval criteria are defined before live runs. A provider cannot be approved
merely because it wins relative to the other; it must also satisfy absolute
thresholds for completion, relevance, attribution, latency, reliability, and
cost. The comparison report records configuration, raw aggregate measurements,
qualitative failures, and one verdict: approve Tavily, approve Exa, approve
both for distinct roles, reject both, or run a named follow-up experiment.

### Experimental providers remain gated after implementation

Adding provider code and Tools controls does not advertise web search to the
checked-in model catalog by default. Deterministic tests and direct smoke tests
can run before catalog enablement. A provider becomes available to ordinary
Chat or Voice Chat only after the physical comparison is reviewed and the
corresponding explicit capability/configuration gate is changed.

This preserves a useful implementation artifact even if neither service meets
the quality bar.

### Preserve common Chat and Voice safety semantics

Normal Chat and Voice Chat consume the same tool lifecycle and source metadata.
Voice Chat may announce a short research state, never speaks protocol or URLs,
does not start TTS for an empty answer, and returns to the listening loop after
a controlled search or incomplete-generation failure.

Provider quota, authentication, rate-limit, timeout, malformed-response, and
network failures map to controlled domain reasons and may trigger the
user-enabled fallback. Cancellation and invalid model arguments do not.

## Risks / Trade-offs

- **A user token can still be recovered on a compromised/rooted device** →
  document the boundary, use platform-backed private storage, minimize token
  lifetime in memory, and never promise protection against a fully compromised
  OS.
- **Direct provider calls reveal queries and network metadata** → disabled by
  default, provider-specific disclosure, explicit activation, and immediate
  disable/delete controls.
- **A provider changes API shape, ranking, pricing, or quota** → isolate
  adapters, validate strictly, record API/config versions, and fail closed.
- **Focused excerpts omit decisive context** → retain source links, compare
  multiple evidence budgets, and require answer-quality thresholds rather than
  optimizing only for size.
- **Web content contains prompt injection or unsafe instructions** → no
  application privileges, untrusted framing, fixed endpoints, bounded plain
  text, source validation, and adversarial corpus cases.
- **Two enabled knowledge tools cause extra model calls** → keep Wikipedia
  semantically narrow, cap calls independently, and isolate tools during the
  comparison.
- **Tavily's two-stage search/extract costs more than Exa's combined path** →
  record provider credits/cost per completed answer, not just per request.
- **Fallback can send one query to two paid services** → require both providers
  to be independently enabled, disclose the behavior, and never fall back after
  success or cancellation.
- **Live web results make exact assertions nondeterministic** → fake-transport
  tests gate correctness; live results characterize quality and reliability
  over repeated paired runs.
- **The active generation-settings change shifts test results** → record its
  effective values and freeze them for every paired run.

## Migration Plan

1. Add provider-neutral focused-evidence contracts and deterministic fixtures.
2. Add private credential storage, backup exclusions, redaction tests, and
   provider configuration UI without enabling model access.
3. Implement Tavily and Exa adapters with fake transports and strict bounds.
4. Add provider-specific direct smoke tests and verify user-owned credentials
   on a physical device.
5. Add the stable `web_search` LiteRT-LM adapter behind an experimental gate.
6. Check in the comparison corpus, scoring rubric, harness, and fixed run
   configuration.
7. Execute paired E2B/E4B runs, publish the comparison report, and make an
   explicit provider verdict.
8. Only after approval, change catalog/config gating for the selected provider
   and repeat normal Chat and Voice Chat acceptance tests.

Rollback disables the experimental web-search capability and removes selected
provider state from new conversations. Provider code and test evidence may
remain dormant. Users can delete stored tokens independently; no conversation
migration is required because raw evidence and credentials are never durable
messages.

## Open Questions

- What absolute completion, latency, relevance, and per-answer cost thresholds
  must a provider meet for approval?
- Should an approved provider coexist with Wikipedia automatically, or should
  the user choose one knowledge mode per conversation?
- Which Android credential-storage implementation best matches the app's
  minimum API level without adding a deprecated security dependency?
- Should the final comparison score citations automatically, manually, or with
  both methods?
