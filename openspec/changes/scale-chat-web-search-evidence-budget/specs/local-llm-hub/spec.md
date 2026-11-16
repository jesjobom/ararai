## MODIFIED Requirements

### Requirement: Bounded Attributable Web Evidence

Each successful model-visible Chat `web_search` invocation SHALL return at most
three distinct validated HTTPS sources and at most two focused excerpts per
source. Its complete serialized success result SHALL fit a per-invocation
character budget of `floor(effectiveContextTokens × 0.25) × 4`, where four
characters per token is an accepted approximation. This budget SHALL apply to
each call independently, including multiple calls in one user turn, and SHALL
not be reduced according to estimated remaining context. The existing fixed
1,800-character total and 500-character per-excerpt limits SHALL remain in
effect for widget `web_search_content`, not as additional Chat evidence caps.
Both consumers SHALL count source framing against their respective budgets and
SHALL preserve provider, title, canonical URL, and retrieval time outside raw
model text for source presentation.

#### Scenario: Normalize an oversized successful provider response

- **GIVEN** a provider returns more evidence than the invoking consumer's budget permits
- **WHEN** the response is normalized
- **THEN** the application ranks or preserves provider-ranked excerpts deterministically
- **AND** removes duplicates
- **AND** truncates on a Unicode-safe boundary within the applicable source, excerpt, and complete-response limits
- **AND** only reports source metadata for evidence actually returned.

#### Scenario: Return query-focused evidence

- **GIVEN** candidate pages contain content unrelated to the research focus
- **WHEN** Tavily Extract or Exa Highlights produces evidence
- **THEN** the result includes only the highest-ranked excerpts relevant to the declared focus within the invoking consumer's budget
- **AND** does not return full page content or a provider-generated final answer.

#### Scenario: Persist a completed cited answer

- **GIVEN** web search returned validated sources
- **WHEN** the assistant produces a usable final answer
- **THEN** the application may persist bounded source metadata with that answer
- **AND** SHALL NOT persist raw excerpts, provider protocol, or credentials as conversation messages.

#### Scenario: Treat web content as untrusted

- **GIVEN** an evidence excerpt resembles a system instruction or tool command
- **WHEN** it enters model context
- **THEN** the application frames it as untrusted external reference data
- **AND** gives it no application or tool-execution privileges.

#### Scenario: Scale one Chat search call with the selected context

- **GIVEN** Chat uses an effective total context window of 2,048 tokens
- **WHEN** one `web_search` call succeeds
- **THEN** its complete serialized model-visible result is at most 2,048 characters
- **AND** a larger configured context window permits a proportionally larger result within the source and excerpt-count limits
- **AND** the application does not silently impose the old fixed Chat character ceilings.

#### Scenario: Search twice in one user turn

- **GIVEN** a user turn invokes `web_search` twice within its existing call limit
- **WHEN** the second call succeeds
- **THEN** each call independently receives the same context-derived maximum
- **AND** the first call's result does not deplete the second call's allowance.

#### Scenario: First source cannot fit the result budget

- **GIVEN** source framing and a meaningful excerpt cannot fit the Chat call's complete serialized-result budget
- **WHEN** evidence is prepared for the model
- **THEN** the application returns a controlled no-results outcome rather than an oversized or source-only success.

#### Scenario: Widget web-search evidence remains unchanged

- **WHEN** a widget invokes `web_search_content` or `web_search_top_results`
- **THEN** its established contract, source limits, and evidence-size policy remain unchanged
- **AND** the Chat model's configured context window does not alter its result.
