# Web-search comparison harness

`WebSearchComparisonHarness` provides the deterministic orchestration boundary for paired Tavily/Exa runs.
The Android-facing runner is responsible for applying the supplied frozen configuration, enabling only the
requested provider, disabling Wikipedia, and starting a fresh native conversation. The harness calls the reset
boundary before every provider/question/run tuple and gives both providers the same number of repetitions.

Each result stores only bounded comparison metadata: provider outcome, evidence/source counts, cost estimate,
provider/model latency, available token counts, completion/citation state, and reviewer scores. It deliberately
does not accept provider tokens, raw evidence, prompts, answers, headers, or provider response bodies.
`WebSearchComparisonReport.toRedactedJson` emits deterministic records and aggregates suitable for a checked-in
report after reviewers add scores and choose the OpenSpec verdict.

Cancellation records the interrupted tuple and stops the remaining run matrix without manufacturing an outcome.

The first physical validation intentionally uses five representative bilingual questions with two runs per
provider/question/model combination (40 runs across E2B and E4B). This is a lean product-validation checkpoint,
not a statistically strong replacement for the complete corpus. The full corpus and at least three repetitions
remain a named follow-up after the initial provider verdict.
