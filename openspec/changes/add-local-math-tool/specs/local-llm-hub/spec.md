## ADDED Requirements

### Requirement: Evidence-based local math engine selection

The project SHALL select the calculator expression engine only after recording a
comparative evaluation of viable approaches covering license, Android
compatibility, maintenance and supply-chain posture, artifact size, expression
grammar, numerical semantics, resource limits, performance, and prevention of
arbitrary code or application access.

#### Scenario: Research changes the proposed capability

- **WHEN** engine research identifies a material restriction, risk, or safe new capability
- **THEN** the active proposal, design, requirements, and implementation tasks are revised consistently
- **AND** strict OpenSpec validation passes before a production dependency or calculator implementation is added.

#### Scenario: Select an expression engine

- **WHEN** the research gate completes
- **THEN** its artifact identifies the selected approach and rejected alternatives with evidence
- **AND** documents the supported grammar, numerical behavior, limits, dependency impact, and residual risks.

### Requirement: Generic application tool boundary

The application SHALL represent local computation through a runtime-neutral
application-tool boundary without treating it as external knowledge retrieval,
while preserving the behavior and provenance contracts of existing knowledge
tools.

#### Scenario: Register tools from different categories

- **GIVEN** an eligible conversation has enabled knowledge and local-compute tools
- **WHEN** the LiteRT-LM conversation is initialized
- **THEN** each tool is registered through its validated structured schema
- **AND** only external knowledge results use source provenance and untrusted-reference framing
- **AND** existing Wikipedia and web-search behavior remains compatible.

### Requirement: Optional local calculator tool

The app SHALL offer an optional `calculator` tool that evaluates bounded
mathematical expressions entirely on-device using the selected documented engine
and returns a structured result to an eligible model.

The initial grammar SHALL be limited to decimal/scientific numeric literals,
parentheses, unary signs, documented arithmetic operators, documented numeric
functions, and the `pi` and `e` constants. Trigonometric inputs SHALL use radians.
Variables, assignment, implicit multiplication, custom definitions, nonnumeric
types, and non-allowlisted evaluator capabilities SHALL be rejected.

#### Scenario: Enable calculator for an eligible model

- **GIVEN** calculator is enabled in Assistant configuration
- **AND** the selected installed model explicitly declares verified calculator tool capability
- **WHEN** a LiteRT-LM conversation is initialized
- **THEN** the app registers the structured `calculator` tool
- **AND** automatic structured tool calling can use it without a command phrase or per-turn user action.

#### Scenario: Calculate a supported expression

- **GIVEN** the calculator tool is registered
- **WHEN** the model submits a valid expression within documented grammar and resource limits
- **THEN** the app evaluates it without network access
- **AND** returns a finite result in a locale-independent structured representation
- **AND** identifies whether the result is exact, rounded, or approximate under the documented DECIMAL128 policy
- **AND** the model can use that result to synthesize the final response.

#### Scenario: Answer without calculation

- **GIVEN** the calculator tool is registered
- **WHEN** the model answers without requesting it
- **THEN** the app performs no calculation
- **AND** normal local generation completes unchanged.

### Requirement: Safe bounded mathematical evaluation

Calculator evaluation SHALL accept only its documented expression grammar,
enforce input and computational limits, support cancellation, and SHALL NOT use
dynamic code evaluation, scripts, reflection, filesystem access, network access,
or arbitrary user-defined executable functions.

#### Scenario: Reject an invalid or unsafe expression

- **WHEN** calculator arguments are malformed, oversized, unsupported, or exceed a complexity limit
- **THEN** the app returns a stable controlled failure to the model
- **AND** performs no partial external action
- **AND** does not fabricate a numeric result.

#### Scenario: Handle an undefined or non-finite result

- **WHEN** evaluation encounters division by zero, a domain error, overflow, or another non-finite outcome
- **THEN** the app returns a controlled failure consistent with the documented numerical policy
- **AND** the model is not given a fabricated finite value.

#### Scenario: Cancel an active calculation

- **WHEN** the conversation turn is cancelled or the calculator exceeds its execution budget
- **THEN** evaluation stops or its late result is discarded
- **AND** the normal generation lifecycle reaches a controlled cancelled or failed state
- **AND** no automatic retry loop is started.

### Requirement: Calculator capability, disclosure, and privacy

The app SHALL persist calculator enablement locally, keep it disabled by default,
explain its supported numerical scope and local execution, and advertise it only
to individually validated models.

#### Scenario: Selected model cannot use calculator

- **GIVEN** calculator is enabled
- **AND** the selected model lacks verified structured calculator capability
- **WHEN** Assistant configuration or a conversation is active
- **THEN** the app reports that calculator is unavailable for the current model
- **AND** does not register a hidden tool, inject a textual tool protocol, or parse ordinary assistant text as a tool call
- **AND** normal local generation remains available.

#### Scenario: Review calculator privacy

- **WHEN** the user reviews calculator in Assistant configuration
- **THEN** the app explains that expressions and results are processed on-device
- **AND** no provider credential or network service is required
- **AND** calculation protocol and intermediate values are not persisted as visible conversation messages.

## MODIFIED Requirements

### Requirement: Instructions and Tools Management

The app SHALL provide an `Assistant configuration` destination for maintaining
mode-specific user instructions, locally persisted enablement for external-
knowledge and local-compute tools, and per-model conversational generation
settings.

#### Scenario: Open Assistant configuration

- **GIVEN** the user is on Home
- **WHEN** the user opens `Assistant configuration`
- **THEN** the action appears immediately above Settings
- **AND** the screen provides `Instructions`, `Tools`, and `Generation` tabs
- **AND** the Tools tab distinguishes external-network tools from local-compute tools.

#### Scenario: Edit instructions independently

- **WHEN** the user edits and saves the normal-Chat or Voice-Chat instruction
- **THEN** the app enforces the documented size limit
- **AND** persists the accepted text locally
- **AND** applies it only to future turns from that interaction mode
- **AND** does not modify already completed messages.

#### Scenario: Review Wikipedia networking

- **GIVEN** Wikipedia is not enabled
- **WHEN** the user reviews the tool
- **THEN** the screen explains that eligible queries and result retrieval use an
  external Wikipedia/MediaWiki service
- **AND** explains that inference and conversation storage remain local
- **AND** no Wikipedia request occurs before enablement.

#### Scenario: Selected model cannot use the enabled tool

- **GIVEN** the Wikipedia preference is enabled
- **AND** the selected model lacks verified Wikipedia tool capability
- **WHEN** the tools screen or a conversation is active
- **THEN** the app reports that Wikipedia is unavailable for the current model
- **AND** does not advertise a hidden tool to that model
- **AND** normal local generation remains available.
