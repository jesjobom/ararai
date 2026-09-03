## MODIFIED Requirements

### Requirement: Generic application tool boundary

The application SHALL resolve every model-callable local-compute and
external-knowledge tool through the shared application-tool registry and
execution boundary without treating local computation as external knowledge
retrieval. Model advertisement SHALL require tool registration, user
enablement, operational readiness, `MODEL` eligibility, and explicit capability
for the selected model, while preserving the behavior and provenance contracts
of existing knowledge tools.

#### Scenario: Register tools from different categories

- **GIVEN** an eligible conversation has enabled and ready knowledge and
  local-compute tools
- **AND** the selected model explicitly supports those registered tools
- **WHEN** the LiteRT-LM conversation is initialized
- **THEN** each `MODEL`-eligible tool is registered through its existing
  validated structured model schema
- **AND** execution reaches its application-owned implementation through the
  shared boundary
- **AND** only external knowledge results use source provenance and
  untrusted-reference framing
- **AND** existing Wikipedia and web-search behavior remains compatible.

#### Scenario: Exclude a widget-only tool from model calling

- **GIVEN** an enabled registered tool declares `WIDGET` eligibility but not
  `MODEL` eligibility
- **WHEN** a LiteRT-LM conversation is initialized
- **THEN** the application does not advertise the tool to the model
- **AND** does not add its identity or schema to native-conversation
  compatibility state.

#### Scenario: Keep model capability consumer-specific

- **GIVEN** an enabled registered tool declares both `MODEL` and `WIDGET`
  eligibility
- **AND** the selected model lacks verified capability for that tool
- **WHEN** a LiteRT-LM conversation is initialized
- **THEN** the tool is not advertised to the selected model
- **AND** its independent widget eligibility remains unchanged.

#### Scenario: Preserve existing model tool contracts

- **WHEN** Wikipedia, provider-neutral web search, or calculator is resolved
  from the shared registry for an eligible model turn
- **THEN** its model-visible name and input schema remain unchanged
- **AND** its current call ceiling, provider policy, result framing, source
  capture, lifecycle events, cancellation, and controlled failures remain in
  force.
