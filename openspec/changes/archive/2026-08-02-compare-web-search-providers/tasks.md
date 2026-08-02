## 1. Provider-Neutral Contract

- [x] 1.1 Define bounded web-search request, focused-evidence, source, provider, and controlled-failure domain models without vendor protocol types.
- [x] 1.2 Define shared validation and normalization for HTTPS sources, duplicate excerpts, Unicode-safe excerpt limits, the 1,800-character envelope, and untrusted-data framing.
- [x] 1.3 Add deterministic unit tests for every shared source, excerpt, total-size, malformed-data, and prompt-injection boundary.

## 2. Private User Credentials

- [x] 2.1 Select and document an Android credential-storage mechanism compatible with the minimum API level and the no-backup/no-export requirements.
- [x] 2.2 Implement separate Tavily and Exa token save, replace, presence, and delete operations without a full-value readback UI.
- [x] 2.3 Add Android backup/export exclusions and automated tests proving tokens do not enter preferences dumps, saved state, logs, diagnostics, conversation storage, or model context.
- [x] 2.4 Add centralized provider diagnostic redaction and tests covering authorization headers, tokens embedded in exceptions, and failed HTTP responses.

## 3. Tools Configuration UI

- [x] 3.1 Add disabled-by-default Tavily and Exa cards with provider-specific disclosure, token configuration, verification, enable/disable, and delete actions.
- [x] 3.2 Model unconfigured, configured-disabled, selected-enabled, incompatible, verifying, authentication-failed, quota/rate-limited, and unavailable states.
- [x] 3.3 Support independent enablement, deterministic Exa-first ordering when both providers are enabled, and Tavily fallback while keeping Wikipedia configuration independent.
- [x] 3.4 Add Compose/UI tests for disclosure acceptance, obscured token entry, recreation without token disclosure, switching providers, token removal, and unsupported models.

## 4. Tavily Provider

- [x] 4.1 Implement a fixed-endpoint Tavily HTTP transport with bounded request/response sizes, timeouts, redirect rejection, cancellation, media-type validation, and controlled status mapping.
- [x] 4.2 Implement Tavily search plus query-focused extraction with generated answers disabled and normalize at most three sources into the shared focused-evidence budget.
- [x] 4.3 Add deterministic fixture tests for success, no results, duplicates, oversized extracts, malformed JSON/UTF-8, authentication, quota, rate limit, timeout, cancellation, and provider drift.
- [x] 4.4 Add a direct user-initiated Tavily smoke test that reports bounded diagnostics without loading a model or exposing the token.

## 5. Exa Provider

- [x] 5.1 Implement a fixed-endpoint Exa HTTP transport with the same network, cancellation, validation, and controlled-status guarantees as Tavily.
- [x] 5.2 Implement Exa Search with query-focused Highlights, explicit character bounds, generated summaries disabled, and shared evidence normalization.
- [x] 5.3 Add deterministic fixture tests matching Tavily's success and complete failure matrix.
- [x] 5.4 Add a direct user-initiated Exa smoke test with the same bounded diagnostic contract.

## 6. LiteRT-LM and Conversation Integration

- [x] 6.1 Implement one stable turn-scoped `web_search` OpenAPI adapter accepting only bounded query, language, and focus arguments.
- [x] 6.2 Bind the ordered enabled provider chain, enforce two model-visible calls per turn, capture sources, emit lifecycle telemetry, and request final synthesis after the last allowance.
- [x] 6.3 Include deterministic enabled-provider order in native conversation compatibility and invalidate retained state on provider enablement, token deletion, or compatibility changes.
- [x] 6.4 Integrate controlled web-search progress, failure, cancellation, source persistence, and incomplete-response behavior into normal Chat.
- [x] 6.5 Integrate the same lifecycle into Voice Chat without empty TTS, spoken protocol/URLs, or failure to resume the hands-free loop.
- [x] 6.6 Add JVM and instrumentation tests with fake providers for schema validation, exact call limits, source capture, provider switching, cancellation, final-answer completion, and Chat/Voice parity.

## 7. Comparison Corpus and Harness

- [x] 7.1 Check in 20–30 bilingual questions spanning stable facts, current facts, news, comparisons, ambiguity, technical documentation, no-result/adversarial cases, and no-search controls.
- [x] 7.2 Define the answer/source scoring rubric and absolute approval thresholds for completion, relevance, attribution, latency, reliability, payload size, and cost before live comparison.
- [x] 7.3 Build a paired-run harness that resets native conversation state, disables Wikipedia and the competing provider, freezes model/prompt/generation/evidence settings, and repeats each provider equally.
- [x] 7.4 Record redacted per-run provider outcome, evidence size, source count, cost estimate, provider/model latency, available token metrics, completion status, citations, and answer score.
- [x] 7.5 Add tests for harness pairing, configuration capture, aggregation, redaction, interrupted runs, and reproducible report generation.

## 8. Physical Validation and Verdict

- [x] 8.1 Run direct Tavily and Exa smoke tests with user-owned credentials on the target Android device; record that validation was manual where API/config versions and network conditions were not captured.
- [x] 8.2 Execute a representative paired subset of five bilingual questions on the approved E2B and E4B configurations with two runs per provider/question/model combination. Defer the complete corpus to a named follow-up validation.
- [x] 8.3 Review incorrect, uncited, incomplete, irrelevant, oversized, slow, rate-limited, and costly cases separately rather than relying only on aggregate scores.
- [x] 8.4 Publish a checked-in comparison report with measurements and one explicit verdict: approve Tavily, approve Exa, approve both for distinct roles, reject both, or require a named follow-up.
- [x] 8.5 Keep both providers catalog/config gated unless the verdict approves ordinary use, then enable only the approved capability and repeat normal Chat and Voice Chat acceptance tests.
- [x] 8.6 Run a direct provider-only comparison over the 20 live-search corpus questions with at most 100 total HTTP requests and at most five request starts per second.
- [x] 8.7 Publish the provider-only measurements and comparative source review separately from the Gemma end-to-end verdict.
- [x] 8.8 Make Exa the deterministic first provider when both are enabled, remove manual priority selection, and retain Tavily as controlled fallback.

## 9. Documentation and Quality Gate

- [x] 9.1 Document Wikipedia versus web-search scope, user-owned token handling, provider disclosure, quotas/costs, source limitations, and token deletion.
- [x] 9.4 Restrict Wikipedia's tool description and system instruction to direct stable encyclopedic lookups and test the exact guidance.
- [x] 9.2 Run targeted unit tests, full JVM tests, lint, debug build, and relevant instrumented tests.
- [x] 9.3 Verify no application-owned provider credential or test token exists in source, generated APK resources, logs, fixtures, reports, or version control.
