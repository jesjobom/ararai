## ADDED Requirements

### Requirement: Basic Markdown Chat Rendering

The Chat screen SHALL render a basic Markdown subset in textual Chat message
content without modifying the stored source text.

#### Scenario: Render block formatting

- **GIVEN** a text message contains Markdown headings, unordered or ordered
  lists, block quotes, fenced code, or horizontal rules
- **WHEN** the message appears in Chat history
- **THEN** each supported block is presented with visually distinct formatting
- **AND** the original Markdown source remains unchanged in session storage.

#### Scenario: Render inline formatting

- **GIVEN** a text message contains bold, italic, inline code, or link syntax
- **WHEN** the message appears in Chat history
- **THEN** the supported inline content is styled without displaying its
  formatting delimiters.

#### Scenario: Render visible reasoning

- **GIVEN** an assistant message contains reasoning text
- **AND** `Show reasoning` is enabled
- **WHEN** the message appears in Chat history
- **THEN** the same supported basic Markdown formatting is applied to the
  visible reasoning text.

#### Scenario: Preserve unsupported or malformed input

- **GIVEN** a message contains unsupported or malformed Markdown syntax
- **WHEN** the message appears in Chat history
- **THEN** the app displays readable text for that content
- **AND** message rendering does not fail.

#### Scenario: Empty streamed response

- **GIVEN** an assistant response has not emitted visible text yet
- **WHEN** its message placeholder appears in Chat history
- **THEN** the existing loading placeholder remains visible.
