## MODIFIED Requirements

### Requirement: Instructions and Tools Management

The app SHALL provide an `Assistant configuration` destination for maintaining
mode-specific user instructions, locally persisted tool enablement, and
per-model conversational generation settings.

#### Scenario: Open Assistant configuration

- **GIVEN** the user is on Home
- **WHEN** the user opens `Assistant configuration`
- **THEN** the action appears immediately above Settings
- **AND** the screen provides `Instructions`, `Tools`, and `Generation` tabs.

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

## ADDED Requirements

### Requirement: Per-Model Conversational Generation Configuration

The app SHALL resolve locally persisted total-context and temperature overrides
independently for each configured Chat model and SHALL fall back to current
catalog defaults when an override is absent.

#### Scenario: Configure one model

- **GIVEN** one Chat model is selected
- **WHEN** the user saves a valid context window or temperature
- **THEN** the override is associated with that stable model ID
- **AND** applies to future normal-Chat and Voice-Chat turns using that model
- **AND** does not change another model's effective settings.

#### Scenario: Return to a previously configured model

- **GIVEN** two models have different saved generation settings
- **WHEN** the user switches away from one model and later selects it again
- **THEN** the app restores that model's saved effective values.

#### Scenario: Restore catalog defaults

- **GIVEN** the selected model has one or more generation overrides
- **WHEN** the user restores defaults
- **THEN** the app removes those overrides
- **AND** resolves the selected model's current catalog values
- **AND** leaves other models' overrides unchanged.

#### Scenario: Reject invalid manual values

- **WHEN** the user enters a non-positive context window or a non-finite or
  negative temperature
- **THEN** the app reports an inline validation error
- **AND** does not persist or apply the invalid value.

### Requirement: Truthful LiteRT-LM Generation Controls

The app SHALL apply each exposed conversational generation setting to its real
LiteRT-LM control and SHALL not present an unsupported independent response
token limit as configurable or enforced.

#### Scenario: Apply total context capacity

- **GIVEN** a valid effective context window is resolved for the selected model
- **WHEN** LiteRT-LM is initialized for a conversational workload
- **THEN** `EngineConfig.maxNumTokens` receives that total input-plus-output
  capacity
- **AND** context projection uses the same effective total capacity.

#### Scenario: Apply sampling temperature

- **GIVEN** a valid effective temperature is resolved for the selected model
- **WHEN** a normal-Chat or Voice-Chat conversation is configured
- **THEN** `SamplerConfig.temperature` receives that value.

#### Scenario: Change a load-bound setting

- **GIVEN** LiteRT-LM retains engine or conversation state
- **WHEN** a future turn resolves an incompatible effective context or sampling
  configuration
- **THEN** the app closes all incompatible native state
- **AND** initializes the requested effective configuration before generation
- **AND** preserves canonical conversation history.

#### Scenario: Review response-limit semantics

- **WHEN** the user views Generation configuration
- **THEN** the app explains that the current runtime controls response stopping
  rather than exposing an independent output-token setting
- **AND** explains that reasoning and final answer share total capacity
- **AND** does not label a projection reserve as a maximum response-token
  limit.

### Requirement: Generation Configuration Experience

The Generation tab SHALL expose effective conversational generation values,
supported controls, model capability, and runtime-backed last-turn diagnostics.

#### Scenario: Review effective configuration

- **GIVEN** a Chat model is selected
- **WHEN** the Generation tab is shown
- **THEN** it identifies the selected model
- **AND** shows the effective total context window and numeric temperature
- **AND** reports whether the model supports reasoning
- **AND** offers restoration of catalog defaults.

#### Scenario: Select a temperature profile

- **WHEN** the user selects `Precise`, `Balanced`, or `Creative`
- **THEN** the app resolves the centralized numeric value for that profile
- **AND** persists it for the selected model
- **AND** displays the effective numeric value.

#### Scenario: Enter a manual temperature

- **WHEN** the user chooses manual temperature and saves a valid value
- **THEN** the exact accepted value is persisted for the selected model
- **AND** used by future conversational turns.

#### Scenario: Show available last-turn metrics

- **GIVEN** the most recent conversational turn produced runtime-backed metrics
- **WHEN** the Generation tab is shown
- **THEN** it may show prefill tokens and throughput, decode tokens and
  throughput, and time to first token
- **AND** does not persist those measurements as Chat messages.

#### Scenario: Metrics are unavailable

- **GIVEN** the runtime did not supply a trustworthy measurement
- **WHEN** the Generation tab is shown
- **THEN** the corresponding metric is reported as unavailable
- **AND** the app does not estimate callback chunks as tokens.

### Requirement: Isolated Benchmark Generation Configuration

The model benchmark SHALL keep fixed benchmark-owned generation parameters and
measurements independent from conversational generation overrides.

#### Scenario: Run benchmark after changing conversational settings

- **GIVEN** the selected model has conversational context or temperature
  overrides
- **WHEN** its benchmark runs
- **THEN** the benchmark uses its documented fixed parameters
- **AND** its metrics describe only that benchmark run
- **AND** it neither reads nor overwrites the conversational last-turn metrics.

### Requirement: Durable Incomplete Assistant Response

The app SHALL distinguish a terminal assistant generation that contains
reasoning but no usable final answer from a complete answer, cancellation, and
failure.

#### Scenario: Generation ends after reasoning only

- **GIVEN** the runtime emits non-blank reasoning
- **AND** emits no usable final answer text
- **WHEN** the generation reaches its terminal completion callback
- **THEN** the assistant message is marked incomplete
- **AND** partial reasoning is preserved
- **AND** the turn is not presented as an ordinary successful ellipsis.

#### Scenario: Present an incomplete response

- **GIVEN** an incomplete assistant message exists
- **WHEN** normal Chat renders it
- **THEN** Chat shows an `Incomplete response` indication and controlled
  explanation
- **AND** shows partial reasoning only when `Show reasoning` is enabled
- **AND** does not guess, repair, or silently replace generated facts.

#### Scenario: Reconstruct legacy and incomplete messages

- **GIVEN** persisted history contains legacy assistant messages without a
  completion-status field and newer incomplete messages
- **WHEN** the conversation is reconstructed after process death
- **THEN** legacy messages default to complete
- **AND** incomplete status and partial reasoning remain available
- **AND** eligible completed history remains unchanged.

### Requirement: Best-Available Final Answer Guidance

The app-owned invariant generation instruction SHALL guide the model to produce
the best available final answer after exhausting tools and to review modern
calendar years before finalizing.

#### Scenario: Reach the tool-call ceiling

- **GIVEN** a turn has used every permitted tool invocation
- **WHEN** the model continues the generation
- **THEN** the invariant instruction directs it to synthesize the best answer
  from available material without another tool request.

#### Scenario: Review a modern calendar year

- **WHEN** the model prepares a final answer containing a modern calendar year
- **THEN** the invariant instruction directs it to verify complete four-digit
  representation
- **AND** application code does not silently rewrite the generated year.
