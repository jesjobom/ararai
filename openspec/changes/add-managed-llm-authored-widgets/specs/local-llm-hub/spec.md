## ADDED Requirements

### Requirement: Isolated widget-authoring model session

The application SHALL start the versioned multi-round widget-authoring protocol
only when the selected installed model explicitly declares the verified
`widget_authoring_pipeline_v1` catalog capability. The historical
`propose_widget` marker MUST NOT implicitly grant eligibility for this protocol.
Each round SHALL use a fresh ephemeral local-model
conversation, advertise exactly one bounded stage-specific capture tool, accept
at most one call, and SHALL NOT advertise or execute normal Chat,
knowledge/calculator, provider, or widget-execution tools. The loaded model
workload may be reused across rounds, but conversation state MUST NOT be reused
implicitly. Protocol v1 SHALL use the closed capture tools
`submit_widget_feasibility`, `submit_widget_algorithm`,
`submit_widget_call_function`, `submit_widget_plan_function`, and
`submit_widget_render_function`; a repair SHALL reuse only the failed stage's
tool and schema.

#### Scenario: Start authorship with an eligible model

- **GIVEN** the selected installed model is explicitly verified for the current
  `widget_authoring_pipeline_v1` protocol version
- **WHEN** the user starts widget creation or prompt-based editing
- **THEN** the application starts the first isolated authoring round with only
  its bounded non-secret objective and required API/tool-contract context
- **AND** advertises only that round's structured capture tool.

#### Scenario: Advance to another authoring stage

- **WHEN** one stage returns exactly one artifact that passes application
  validation
- **THEN** the application freezes that artifact and starts a fresh conversation
  for the next stage with only its required immutable inputs
- **AND** does not rely on hidden conversation history or let the next stage
  rewrite the accepted artifact.

#### Scenario: Keep authorship out of normal conversations

- **WHEN** normal Chat or Voice Chat initializes a model conversation
- **THEN** no widget-authoring protocol or stage tool is advertised or accepted
- **AND** existing tool availability and retained-conversation compatibility
  remain unchanged.

#### Scenario: Capture rather than execute an authoring-stage call

- **WHEN** the authoring model calls the current stage tool with bounded
  arguments
- **THEN** the authoring adapter captures those arguments as one untrusted
  intermediate artifact for application validation
- **AND** does not execute JavaScript, invoke an application tool, persist a
  widget, or continue an automatic tool loop inside that generation.

#### Scenario: Run a targeted repair round

- **WHEN** deterministic validation rejects one stage and the attempt retains
  repair/time/generation budget
- **THEN** the application starts one fresh repair conversation scoped to that
  stage, its frozen dependencies, relevant contract, and controlled failure code
- **AND** does not provide secrets, provider output, arbitrary exception text,
  unrelated artifacts, or authority outside the frozen envelope.

#### Scenario: Enforce bounded inference policy

- **WHEN** an authoring or repair round starts
- **THEN** the application applies checked-in context/output reserve,
  temperature, per-round timeout, total deadline, generation-count, and repair
  limits without changing saved Chat preferences
- **AND** refuses to start a round whose bounded input and required output
  reserve cannot fit.

#### Scenario: Cancel widget authorship

- **WHEN** the user cancels or leaves any active authoring or repair stage
- **THEN** model generation, the ephemeral conversation, and accumulated
  in-memory artifacts are released
- **AND** a late stage artifact is discarded without changing normal Chat or
  stored widgets.
