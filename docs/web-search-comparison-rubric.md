# Tavily versus Exa comparison rubric

The thresholds below are fixed before live paired runs. Relative superiority is
not sufficient: a provider must meet every applicable absolute release gate.

## Fixed paired configuration

- Same physical device, app commit, debug build, model bundle, system
  instruction, context window, temperature, top-p, reasoning setting, question,
  run count, and network class.
- Fresh native conversation for each provider/question/run tuple.
- Wikipedia and the competing provider disabled.
- `web_search` limited to two invocations, three sources, two excerpts per
  source, 500 characters per excerpt, and 1,800 characters total.
- Tavily generated answers and Exa generated summaries disabled.
- Current lean validation: two runs per provider/question/model combination across the five-question
  representative subset. A future full comparison SHALL restore at least three runs across the complete corpus.

## Automatic release gates

- Usable final-answer completion: at least 95% of eligible runs.
- Evidence-envelope compliance: 100%; any oversize or invalid-source result is
  a provider-adapter defect.
- Credential/protocol leakage: 0 occurrences.
- Controlled error mapping for authentication, quota, rate limit, timeout,
  malformed response, and cancellation: 100%.
- Successful provider response rate, excluding deliberate failure cases: at
  least 95%.
- Median provider/tool latency: at most 6 seconds.
- 95th-percentile provider/tool latency: at most 12 seconds.
- Mean estimated provider cost per completed answer: at most USD 0.02 under the
  tested public plan.

## Reviewed quality gates

Two reviewers score each applicable answer from 0 to 2:

- **Answer correctness**: 0 unsupported/wrong, 1 partly supported, 2 supported.
- **Source relevance**: 0 irrelevant, 1 mixed, 2 directly relevant.
- **Attribution**: 0 missing/fabricated, 1 incomplete, 2 sources support claims.
- **Freshness**: 0 stale, 1 unclear, 2 appropriate date/version evidence.
- **Uncertainty**: 0 fabricates, 1 vague, 2 clearly handles ambiguity/no result.

Approval requires:

- mean correctness at least 1.7;
- mean source relevance at least 1.7;
- mean attribution at least 1.8;
- no fabricated source URL;
- no answer scoring 0 for uncertainty in a no-result or ambiguous case; and
- no material regression in no-search controls compared with tools disabled.

## Required verdict

The final report must choose exactly one:

- approve Tavily;
- approve Exa;
- approve both for explicitly distinct roles;
- reject both; or
- require a named follow-up experiment with a concrete unresolved question.
