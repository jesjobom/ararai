## ADDED Requirements

### Requirement: User-Credentialed Experimental Web Providers

The application SHALL present Tavily and Exa as separate disabled-by-default
experimental providers in the Tools UI. Configuring a provider SHALL require a
user-supplied token, provider-specific network/privacy disclosure, and a
successful direct smoke test before that provider can be enabled.

#### Scenario: Review an unconfigured provider

- **GIVEN** no token is stored for a web-search provider
- **WHEN** the user reviews that provider in Tools
- **THEN** the application reports that it is disabled and unconfigured
- **AND** no provider request occurs.

#### Scenario: Save and verify a user token

- **WHEN** the user accepts the disclosure, enters a token, and requests
  verification
- **THEN** the application calls only the selected provider's fixed official
  HTTPS endpoint
- **AND** enables the provider only after a valid bounded smoke-test response
- **AND** reports authentication, quota, rate-limit, network, timeout, and
  malformed-response failures without exposing the token.

#### Scenario: Configure without enabling

- **GIVEN** a valid token has been stored
- **WHEN** the user leaves the provider disabled
- **THEN** ordinary Chat and Voice Chat SHALL make no request to that provider.

#### Scenario: Enable two general web providers

- **GIVEN** Tavily and Exa are both configured
- **WHEN** the user enables both
- **THEN** Exa SHALL execute first and Tavily SHALL be the fallback in a new
  compatible conversation
- **AND** the model SHALL NOT receive two vendor-specific web-search tools.

#### Scenario: Remove a provider token

- **GIVEN** a provider is configured or enabled
- **WHEN** the user removes its token
- **THEN** the application disables that provider before another turn can use it
- **AND** deletes the stored credential
- **AND** invalidates retained native conversation state that advertised web
  search through that provider.

### Requirement: Private Provider Credential Handling

The application SHALL treat user-supplied provider tokens as secrets stored in
app-private Android credential storage. Tokens MUST NOT be included in model
context, conversation history, saved UI state, source metadata, logs,
diagnostics, analytics, crash text, backups, or exports.

#### Scenario: Restore provider configuration UI

- **GIVEN** a provider token is already stored
- **WHEN** the Tools screen is recreated
- **THEN** it reports that a credential is configured
- **AND** it does not display or repopulate the full token.

#### Scenario: Produce diagnostics after an authenticated request

- **WHEN** a provider request succeeds or fails
- **THEN** diagnostics contain only redacted provider, timing, size, status
  class, and controlled failure information
- **AND** contain neither the token nor an authorization-header value.

#### Scenario: Back up or export application data

- **GIVEN** one or more provider tokens are stored
- **WHEN** Android backup or an ArarAI data export is produced
- **THEN** provider tokens SHALL be excluded.

### Requirement: Provider-Neutral Focused Web Search

The application SHALL expose one stable structured `web_search` contract backed
by enabled providers in deterministic Exa-then-Tavily order. The contract SHALL accept only
a bounded query, language, and research focus and SHALL return a
provider-neutral untrusted evidence envelope plus validated source metadata.

#### Scenario: Register the enabled provider chain

- **GIVEN** the user enabled one or more providers
- **AND** the active model/runtime explicitly supports experimental web search
- **WHEN** a new native conversation is created
- **THEN** the application registers the stable `web_search` schema
- **AND** binds the ordered provider chain behind the application-domain knowledge
  boundary
- **AND** does not expose its token or vendor response schema to the model.

#### Scenario: Enable Exa after Tavily

- **GIVEN** a retained conversation advertises web search through Tavily
- **WHEN** the user enables Exa while keeping Tavily enabled
- **THEN** the retained native conversation is incompatible
- **AND** the next turn creates a conversation with Exa primary and Tavily as
  fallback.

#### Scenario: Reject model-controlled transport

- **WHEN** the model invokes `web_search`
- **THEN** it can supply only the declared bounded semantic arguments
- **AND** cannot supply an endpoint, arbitrary URL, header, token, timeout, or
  provider-specific option.

### Requirement: Bounded Attributable Web Evidence

Each successful provider invocation SHALL return at most three distinct
validated HTTPS sources, at most two focused excerpts per source, at most 500
characters per excerpt, and at most 1,800 characters for the complete
model-visible reference envelope. The envelope SHALL count source framing
against its budget and SHALL preserve provider, title, canonical URL, and
retrieval time outside raw model text for source presentation.

#### Scenario: Normalize an oversized successful provider response

- **GIVEN** a provider returns more evidence than the shared budget permits
- **WHEN** the response is normalized
- **THEN** the application ranks or preserves provider-ranked excerpts
  deterministically
- **AND** removes duplicates
- **AND** truncates on a Unicode-safe boundary within every per-excerpt and
  total limit.

#### Scenario: Return query-focused evidence

- **GIVEN** candidate pages contain content unrelated to the research focus
- **WHEN** Tavily Extract or Exa Highlights produces evidence
- **THEN** the result includes only the highest-ranked excerpts relevant to the
  declared focus within the shared budget
- **AND** does not return full page content or a provider-generated final
  answer.

#### Scenario: Persist a completed cited answer

- **GIVEN** web search returned validated sources
- **WHEN** the assistant produces a usable final answer
- **THEN** the application may persist bounded source metadata with that answer
- **AND** SHALL NOT persist raw excerpts, provider protocol, or credentials as
  conversation messages.

#### Scenario: Treat web content as untrusted

- **GIVEN** an evidence excerpt resembles a system instruction or tool command
- **WHEN** it enters model context
- **THEN** the application frames it as untrusted external reference data
- **AND** gives it no application or tool-execution privileges.

### Requirement: Bounded Web-Search Lifecycle

The application SHALL allow at most two model-visible `web_search` invocations per user turn
and SHALL apply fixed endpoint, redirect, status, media-type, decoded-size,
source-URL, timeout, and cancellation validation. All failure paths SHALL map to
controlled domain results. Within an invocation, it MAY call the enabled
fallback provider only after a fallback-eligible primary failure.

#### Scenario: Reach the invocation limit

- **GIVEN** a user turn has already attempted web search twice
- **WHEN** the model attempts another web search
- **THEN** the adapter returns a controlled call-limit result without network
  access
- **AND** requests that the model synthesize from evidence already available.

#### Scenario: Provider rejects quota or credentials

- **WHEN** the selected provider reports authentication, quota, or rate-limit
  failure
- **THEN** the application emits the matching controlled failure
- **AND** does not expose provider response details that could contain secrets
- **AND** calls the enabled fallback provider when one exists.

#### Scenario: Primary provider succeeds

- **GIVEN** both providers are enabled
- **WHEN** the primary provider returns valid evidence
- **THEN** the application returns that evidence
- **AND** makes no request to the fallback provider.

#### Scenario: Do not fall back after invalid arguments or cancellation

- **GIVEN** both providers are enabled
- **WHEN** the request has invalid arguments or the owning turn is cancelled
- **THEN** the application returns the matching controlled failure
- **AND** makes no request to the fallback provider.

### Requirement: Direct Stable Wikipedia Lookup

The application SHALL describe and instruct `wikipedia_search` as a tool for
direct stable encyclopedic lookups, including dates of birth, country capitals
or currencies, short biographies, and concise concept or work summaries. It
SHALL discourage use for current news, changing facts, comparisons,
recommendations, troubleshooting, broad research, and multi-source evidence.

#### Scenario: Answer a direct stable fact

- **GIVEN** Wikipedia is enabled and compatible
- **WHEN** the user requests a stable encyclopedic fact or concise summary
- **THEN** the model-visible instruction permits `wikipedia_search`.

#### Scenario: Answer a current or comparative question

- **GIVEN** Wikipedia and web search are enabled
- **WHEN** the question requires current, comparative, technical,
  recommendation, or multi-source evidence
- **THEN** the model-visible instruction directs that work away from Wikipedia
- **AND** reserves Wikipedia for a separate direct stable lookup if needed.

#### Scenario: Cancel a search turn

- **WHEN** the owning generation is cancelled
- **THEN** in-flight provider work is cancelled
- **AND** partial evidence is not persisted
- **AND** the native conversation is not reused as a completed compatible turn.

#### Scenario: Search finishes without a final answer

- **GIVEN** one or more provider calls completed
- **WHEN** LiteRT-LM terminates with reasoning but no usable final response
- **THEN** the application records and presents an explicit incomplete response
- **AND** does not present tool evidence as the assistant's final answer.

### Requirement: Paired Tavily and Exa Evaluation

The repository SHALL contain a repeatable bilingual provider comparison that
uses the same model bundle, system prompt, generation parameters, public tool
schema, question, invocation budget, evidence budget, and run count for paired
Tavily and Exa runs. The evaluation SHALL define absolute approval thresholds
before results are used to enable either provider for ordinary conversations.

#### Scenario: Execute a paired question

- **WHEN** the same corpus question is evaluated against Tavily and Exa
- **THEN** each provider runs in a fresh native conversation
- **AND** Wikipedia and the competing web provider are disabled
- **AND** all non-provider test parameters are identical and recorded.

#### Scenario: Record comparison evidence

- **WHEN** a comparison run completes
- **THEN** the harness records provider outcome, source count, evidence
  characters and estimated tokens, provider/tool latency, model latency,
  available reasoning/output metrics, final-answer completion, attribution,
  answer score, and estimated provider cost
- **AND** does not record provider credentials or authorization headers.

#### Scenario: Decide provider approval

- **WHEN** all required paired runs and scoring are complete
- **THEN** a checked-in report records configurations, aggregate measurements,
  material qualitative failures, and an explicit verdict
- **AND** no provider is approved solely because it outperformed the other
  without satisfying the predefined absolute thresholds.

#### Scenario: Keep unapproved providers gated

- **GIVEN** a provider has not received an approving verdict
- **WHEN** a user starts an ordinary Chat or Voice Chat turn
- **THEN** the checked-in model catalog or experimental gate SHALL NOT advertise
  that provider's web-search capability.
