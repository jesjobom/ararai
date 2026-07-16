# Change: Allow Uninterrupted Long Chat Generations

## Why

Long streamed responses currently keep the message list aligned to the start of
the growing assistant message, allowing new text to disappear behind the input
area. LiteRT-LM also cancels generation after counting callback chunks as
tokens, which can terminate a valid response with a `Task cancelled` error.

## What Changes

- Follow the actual end of a streaming response only while the user has not
  taken control of the message list.
- Stop automatic following as soon as the user drags the list, allowing free
  scrolling during generation.
- Resume automatic following when the user explicitly returns to the bottom.
- Remove the LiteRT-LM callback-chunk cancellation that incorrectly acts as an
  output limit.
- Keep the explicit `Cancel Generation` action available.

## Impact

- Touches Chat message-list scrolling and LiteRT-LM streaming behavior.
- Long generations continue until the runtime completes, fails, the user
  cancels, or app/navigation lifecycle cancellation occurs.
