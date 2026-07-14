## ADDED Requirements

### Requirement: Structured llama.cpp Chat Template Input

The llama.cpp runtime SHALL apply GGUF chat templates to structured chat
messages instead of to a preformatted transcript embedded inside one user
message.

#### Scenario: Pass separate roles to native chat template

- **GIVEN** a text-only llama.cpp generation request includes a system prompt,
  prior user/assistant turns, and a current user prompt
- **WHEN** generation starts for a model that exposes a GGUF chat template
- **THEN** the native template receives separate `system`, `user`, and
  `assistant` role messages in chronological order
- **AND** the current user message is the last supplied chat message
- **AND** generation uses the formatted template output with assistant
  generation prompt enabled.

#### Scenario: Fallback without native chat template

- **GIVEN** a text-only llama.cpp generation request includes structured chat
  messages
- **AND** the loaded GGUF model does not expose a usable chat template
- **WHEN** generation starts
- **THEN** the app falls back to a plain transcript representation
- **AND** the fallback transcript preserves the same chronological roles and
  current user prompt.
