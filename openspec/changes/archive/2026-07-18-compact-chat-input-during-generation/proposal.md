# Change: Compact the Chat input during generation

## Why

While the model is generating, Chat keeps the disabled message field, send
button, attachments, and cancel action visible. A long submitted prompt makes
this bottom area unnecessarily tall and obscures the streamed response the user
is trying to read.

## What Changes

- Replace the entire Chat composer with a compact cancel-generation action
  while generation is active.
- Restore the composer and its submitted draft after cancellation or failure.
- Keep the existing successful-completion behavior, which clears the submitted
  draft for the next message.

## Impact

- Affected spec: `local-llm-hub`
- Affected code: Chat input presentation and Chat ViewModel regression tests
- Persistence, inference, model lifecycle, and networking: unchanged
