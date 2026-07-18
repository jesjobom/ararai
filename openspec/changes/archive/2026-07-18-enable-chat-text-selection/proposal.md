# Change: Enable native text selection in Chat messages

## Why

Chat history is currently rendered as non-selectable Compose text. Users cannot
long-press a response or prompt to select and copy all or part of its text with
the standard Android interaction.

## What Changes

- Make textual message content selectable with Android's native long-press
  handles and contextual copy action.
- Support selection in both user and assistant messages, including Markdown and
  optional reasoning text.
- Keep message controls and media interactions outside the selectable region.
- Preserve vertical message layout when reasoning and final text are both shown.
- Use selection and formula colors that contrast with their local message or
  reasoning backgrounds.

## Impact

- Affected spec: `local-llm-hub`
- Affected code: Chat message presentation
- Persistence, inference, privacy, and networking: unchanged
