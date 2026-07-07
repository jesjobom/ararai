## MODIFIED Requirements

### Requirement: Fixed Model Configuration

Phase 1 SHALL include checked-in configuration for exactly one GGUF model and
its default inference limits.

#### Scenario: Parse configured model

- **WHEN** the app starts
- **THEN** it can parse a single configured model entry
- **AND** the entry defines the model ID, source URL, expected local path,
  integrity metadata, and default inference parameters
- **AND** the default inference parameters include context size, sampling
  values, and maximum generated tokens
- **AND** no model picker or user-facing model choice is exposed.

### Requirement: Real Chat Generation Flow

The chat screen SHALL use the real local engine to generate assistant text while
preserving the existing conversation behavior and configured inference limits.

#### Scenario: Stream real assistant output

- **GIVEN** the configured model is available
- **AND** the real local engine is loaded or can be loaded
- **AND** the user typed a non-blank prompt
- **WHEN** the user sends the prompt
- **THEN** the app appends the user message to the conversation
- **AND** creates an assistant message for streamed output
- **AND** formats the prompt using the loaded model's chat template when
  available
- **AND** limits generated output using the configured maximum generated tokens
- **AND** appends generated token text as it arrives
- **AND** re-enables sending after generation completes.

#### Scenario: Block concurrent generation

- **GIVEN** model loading or generation is already in progress
- **WHEN** the chat screen renders
- **THEN** the send action is disabled
- **AND** no second generation request starts.

#### Scenario: Surface real generation failure

- **GIVEN** the real local engine reports a generation failure
- **WHEN** generation fails
- **THEN** the chat screen shows an error state
- **AND** preserves the conversation messages already shown
- **AND** allows the user to edit the prompt and try again when sending is
  otherwise allowed.

## ADDED Requirements

### Requirement: Configured Generation Token Limit

The app SHALL read the maximum generated-token count from checked-in model
configuration.

#### Scenario: Use configured max tokens

- **GIVEN** the configured model is available
- **AND** the model configuration defines `inference.maxTokens`
- **WHEN** the chat runtime starts generation
- **THEN** the real local engine passes that maximum token count to native
  generation
- **AND** the value is not hardcoded in the engine.

#### Scenario: Reject invalid max tokens

- **GIVEN** the configured model declares a non-positive `inference.maxTokens`
- **WHEN** the app parses model configuration
- **THEN** parsing fails with a configuration error.
