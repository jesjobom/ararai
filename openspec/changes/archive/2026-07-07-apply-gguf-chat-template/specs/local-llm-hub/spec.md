## MODIFIED Requirements

### Requirement: Real Chat Generation Flow

The chat screen SHALL use the real local engine to generate assistant text while
preserving the existing conversation behavior and formatting prompts for
instruct/chat models.

#### Scenario: Stream real assistant output

- **GIVEN** the configured model is available
- **AND** the real local engine is loaded or can be loaded
- **AND** the user typed a non-blank prompt
- **WHEN** the user sends the prompt
- **THEN** the app appends the user message to the conversation
- **AND** creates an assistant message for streamed output
- **AND** formats the prompt using the loaded model's chat template when
  available
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

### Requirement: GGUF Chat Template Formatting

The native local inference runtime SHALL format user prompts with the loaded
GGUF model's chat template before generation when the model provides one.

#### Scenario: Format single-turn chat prompt

- **GIVEN** a loaded GGUF model exposes a chat template
- **WHEN** the user sends a prompt
- **THEN** the native runtime formats a single user message with assistant
  generation enabled
- **AND** tokenizes the formatted prompt instead of the raw user text.

#### Scenario: Fallback when no template is available

- **GIVEN** a loaded GGUF model does not expose a usable chat template
- **WHEN** the user sends a prompt
- **THEN** the native runtime falls back to the raw user prompt
- **AND** generation still proceeds without crashing.
