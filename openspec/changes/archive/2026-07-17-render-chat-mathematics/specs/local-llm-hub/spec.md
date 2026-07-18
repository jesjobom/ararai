## ADDED Requirements

### Requirement: Mathematical Chat notation

The Chat SHALL render supported LaTeX-delimited mathematical notation in model
responses locally and in a form that is visually distinct from literal TeX
source.

#### Scenario: Inline mathematical notation

- **WHEN** assistant Markdown contains a complete expression delimited by
  `$...$` or `\(...\)`
- **THEN** the Chat renders the expression inline with the surrounding content
- **AND** retains the surrounding Markdown text and styling

#### Scenario: Display mathematical notation

- **WHEN** assistant Markdown contains a complete expression delimited by
  `$$...$$` or `\[...\]`
- **THEN** the Chat renders the expression as a separate display formula
- **AND** the formula remains readable within the message width

#### Scenario: Incomplete or invalid mathematical notation

- **WHEN** a response contains an unclosed delimiter or an expression that the
  renderer cannot parse
- **THEN** the Chat preserves the original source as readable text
- **AND** generation and message rendering continue without failure

#### Scenario: Non-mathematical dollar sign

- **WHEN** response text uses a dollar sign as currency or escapes a delimiter
- **THEN** the Chat preserves it as ordinary text

#### Scenario: Local formula rendering

- **WHEN** mathematical notation is rendered
- **THEN** rendering does not require network access or a hosted service
