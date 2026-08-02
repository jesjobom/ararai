# Lean Tavily versus Exa device comparison

## Verdict

**Require a named follow-up experiment before approving an automatic/default
provider. Keep both as explicit BYOK options, disabled by default.**

Both adapters passed the transport-level gates exercised by this run, but neither
provider/model combination produced reliable enough answers to pass the reviewed
quality gates. A subsequent direct-retrieval comparison isolated the providers
from Gemma and found Exa directionally stronger. Tavily and Exa remain available
for explicit user configuration while disabled by default.

## Run identity

- Validation date: 2026-08-02
- Target device: Samsung SM-S901E
- Application base commit: `9e1e7c8` plus the uncommitted comparison change
- Models: `gemma-4-e2b-it-litert-lm` (`181938105e0e`) and
  `gemma-4-e4b-it-litert-lm` (`0b2a8980ce15`)
- Questions: `pt-current-android`, `en-current-kotlin`,
  `en-compare-databases`, `pt-news-ai`, and `pt-ambiguous-jaguar`
- Matrix: 5 questions x 2 models x 2 providers x 2 runs = 40 runs
- Frozen generation settings: 6,144 context tokens, temperature 0.2, top-p
  0.9, reasoning disabled, instruction fingerprint `0356967efb92651f`

The source artifacts were retained outside the repository at:

- `artifacts/ararai/web-search-comparison-report.json`
  (`eafafdded1dbca8040780bfb19e4a3708962b2c9825709016387a3edb62092de`)
- `artifacts/ararai/web-search-comparison-review.jsonl`
  (`3d2463501dd64584cbb083f6eb8561de0389ae184fdf3795b25844d3d13b770d`)

## Automatic gates

| Gate | Tavily | Exa | Result |
| --- | ---: | ---: | --- |
| Completed answers | 20/20 | 20/20 | pass |
| Successful provider outcomes | 20/20 | 20/20 | pass |
| Interrupted runs | 0 | 0 | pass |
| Evidence envelope at or below 1,800 characters | 20/20 | 20/20 | pass |
| Median provider latency | 1,645 ms | 942 ms | pass |
| Nearest-rank p95 provider latency | 4,972 ms | 7,081 ms | pass |
| Answers containing a literal HTTPS citation | 4/20 | 0/20 | fail |
| Cost gate | not captured | not captured | not assessable |

No credential, authorization header, bearer token, or API-key-shaped value was
found in either exported artifact. The generated aggregate currently reports a
zero mean cost when all per-run cost values are `null`; that value is not a real
zero-cost measurement and must not be used to pass the USD 0.02 cost gate.

## Separate failure review

### Incorrect or unsupported

- Tavily's E2B Android answers returned Android Oreo/Android 14 instead of the
  current stable release. Its E4B answers returned Android 16 and omitted the
  requested release date.
- Tavily's E4B Kotlin answers contradicted the newer 2.4.10 result present in
  the same test set and selected 2.3.20 instead.
- Exa's Android answers disagreed on the release date between E2B and E4B.
- The news answers contain claims that are stale, weakly sourced, or apparently
  fabricated, including `Claude Mythos`, `Gemini Spark`, and containment-escape
  claims attributed to OpenAI.
- Jaguar answers consistently assumed the automaker without acknowledging the
  intended ambiguity. Several answers also promoted questionable `Type 01` or
  `I-Type` names as production vehicles.

### Uncited or unverifiable

- Exa produced no literal source URL in any of its 20 final answers.
- Tavily produced literal source URLs in only 4 of 20 answers. Bracket markers
  such as `[1]` were often emitted without a resolvable source URL.
- The review artifact contains final answers but not the normalized evidence or
  source URL set. Consequently, two reviewers cannot verify attribution,
  freshness, source relevance, or fabricated URLs from the retained artifacts.
  The reviewed numeric score thresholds are therefore not claimable.

### Incomplete or irrelevant

- Exa's PostgreSQL comparison frequently omitted the AWS RDS version list.
- Tavily's E4B PostgreSQL runs stopped at the tool-call limit without answering
  the comparison.
- Several Tavily news results were old, in Spanish despite a Portuguese prompt,
  or generic rather than dated announcements from the requested week.

### Oversized, slow, rate-limited, and costly

- No evidence envelope exceeded 1,800 characters.
- No authentication, quota, rate-limit, timeout, cancellation, or malformed
  response outcome occurred in this successful matrix.
- One Tavily request took 11,903 ms, below the fixed 12-second per-run boundary
  but well above its median. Exa had two requests above 6 seconds (7,081 and
  9,381 ms). Provider p95 remained below 12 seconds for both.
- Provider cost was not recorded, so the cost gate remains unvalidated.

## Follow-up requirements

Before either provider can be approved as the automatic/default option, the
comparison capture must retain a bounded source manifest suitable for offline
review, preserve unknown cost as unknown in aggregates, and make citations
resolvable in final answers. A new paired physical-device run must then be
reviewed by two people against the fixed rubric. The complete 24-question corpus
with at least three repetitions remains the named follow-up validation after
those defects are corrected.
