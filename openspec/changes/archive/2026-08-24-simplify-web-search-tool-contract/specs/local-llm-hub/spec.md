## MODIFIED Requirements

### Requirement: Provider-Neutral Focused Web Search

The application SHALL expose one stable structured `web_search` contract backed
by enabled providers in deterministic Exa-then-Tavily order. The model-facing
contract SHALL accept exactly one required bounded `query`. The application
SHALL derive provider-only language and research-focus metadata and SHALL return
a provider-neutral untrusted evidence envelope plus validated source metadata.

#### Scenario: Model requests current web evidence

- **WHEN** a compatible model emits `web_search` with one valid `query` string
- **THEN** the application derives bounded provider metadata and executes the
  configured web-search provider through the existing validated boundary

#### Scenario: Model emits additional arguments

- **WHEN** a `web_search` invocation contains an argument other than `query`
- **THEN** the application rejects the invocation before provider execution

#### Scenario: Local language metadata is unavailable

- **WHEN** the application cannot resolve a valid local ISO language code
- **THEN** it uses the deterministic English fallback for evidence metadata

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
