# Direct Tavily versus Exa retrieval comparison

## Scope

This benchmark isolates provider retrieval from Gemma synthesis. It ran the 20
live-search questions in `web-search-comparison-corpus.json` once against each
provider on 2026-08-02.

- 40 provider/question records
- 60 HTTP requests: 40 Tavily (`search` plus `extract`) and 20 Exa
- hard ceiling: 100 HTTP requests
- global start-rate ceiling: 4 requests per second
- no automatic retries
- 20/20 successful provider outcomes for each provider
- no authentication, quota, rate-limit, timeout, or malformed-response failures

Raw redacted result:
`artifacts/ararai/web-search-direct-comparison.json`
(`360d13bbd28f3072e622d06a79f0b5e70eacf92920ce0a700d48035d8a57ca35`).

## Measurements

| Measurement | Tavily | Exa |
| --- | ---: | ---: |
| Successful questions | 20/20 | 20/20 |
| HTTP requests | 40 | 20 |
| Mean end-to-end retrieval latency | 2,491 ms | 1,469 ms |
| Median end-to-end retrieval latency | 2,359.5 ms | 1,434.5 ms |
| Nearest-rank p95 latency | 3,686 ms | 1,813 ms |
| Sources retained | 44 | 57 |
| Mean sources per question | 2.20 | 2.85 |
| Focused-evidence characters retained | 35,435 | 27,327 |
| Mean focused-evidence characters | 1,771.75 | 1,366.35 |

Exa used half as many HTTP requests, was about 41% faster by mean latency, and
returned more distinct sources. Tavily returned about 30% more excerpt text,
usually filling the 1,800-character envelope, but additional text was often not
additional relevant evidence.

## Comparative source review

A manual source/evidence review preferred Exa clearly in 14 of the 20 questions,
preferred Tavily clearly in none, and considered six inconclusive or failures for
both. This is a directional result from one run per question, not a statistically
stable universal score.

### Current facts

- **Android:** Exa returned Android 17 sources from AOSP and Android Developers.
  Tavily returned a 2025 third-party Android 16 article and a generic YouTube
  history video.
- **Kotlin:** Exa returned the JetBrains GitHub releases for 2.4.0 and 2.4.10.
  Tavily found the official Kotlin release page, but its extracted excerpt was
  anchored on old 1.9.x rows.
- **OpenAI model:** Exa returned three current official OpenAI pages. Tavily
  returned a community thread and the original 2020 API announcement.
- **Pixel:** both found the Google Store, but Exa returned three Google-owned
  sources while Tavily mixed the store with a carrier page.

### News

- **Android developer news:** Exa returned July 2026 official Android/Google
  announcements. Tavily's primary official result was from Google I/O in May,
  outside the requested current month.
- **AI news:** neither provider passed confidently. Exa was fresher and included
  one official Google Blog result, but two sources were low-authority AI news
  aggregators. Tavily returned two advertising-industry articles that did not
  establish two major AI announcements from the requested week.

### Comparisons and technical documentation

- **AWS RDS versus Cloud SQL:** Exa returned the exact official AWS and Google
  version pages. Tavily returned the Google page plus a generic third-party cloud
  comparison.
- **Compose requirements and password fields:** Exa consistently selected
  Android Developers API/reference pages. Tavily mixed weak third-party pages or
  Stack Overflow with one official page.
- **Android Keystore:** Exa returned three Android Developers security pages.
  Tavily returned Stack Overflow and a Medium-family blog.
- **LiteRT-LM:** both found official Google documentation, but Exa additionally
  returned the exact upstream `Config.kt` sources containing the configuration
  fields.
- **MediaWiki extracts:** Exa returned the TextExtracts API page and upstream
  implementation. Tavily returned broader MediaWiki query/content pages whose
  excerpts did not expose the requested exact parameters.
- **Provider and pricing comparisons:** both were affected by vendor-authored or
  unaffiliated comparison pages. Neither receives a confident preference.

### Ambiguity and no-result behavior

- **Mercury:** Tavily failed badly, returning dictionary entries for the word
  “what”. Exa returned results for the planet and the Phoenix Mercury team, which
  at least exposed the ambiguity, but still did not produce a controlled
  clarification outcome.
- **Jaguar:** both assumed the automaker and failed the ambiguity objective.
- **Fictional AraraQuantumDB and ZXQ build:** both returned lexical near-matches
  rather than `no_results`. A provider success must therefore not be treated as
  proof that credible evidence exists.

### Prompt-injection research

Exa selected stronger security sources overall, including Palo Alto Unit 42,
OWASP, Forcepoint, and Kaspersky. Tavily returned useful material for the English
case, but its Portuguese case relied on a legal PDF and YouTube.

## Product decision

Keep both providers in ArarAI as explicit BYOK options, disabled by default.
Exa is the stronger provisional default recommendation for current facts,
official technical documentation, source diversity, latency, and request
efficiency. Tavily remains useful as an alternative and produced larger focused
evidence payloads, but this run did not identify a category where its source set
was clearly superior.

The application must not present a timeless global star rating from this sample.
Any displayed note should be dated, identify the corpus/run count, and report
separate dimensions. At least one repeated run and a fresh news-specific sample
are required before publishing a stable comparative score in the UI.
