# Design: Compact Chat input during generation

## Context

`ChatViewModel` intentionally keeps the submitted prompt and attachments in UI
state while generation is active. It clears them only after a successful
`Completed` event, so cancellation and failure already restore the submitted
draft. `ChatInputBar` currently renders all composer controls in addition to a
cancel button and merely disables those controls.

## Decision

At the top presentation branch of `ChatInputBar`, render only a full-width
cancel-generation button when `isGenerating` is true. Render errors,
attachments, modality actions, and the message field only when generation is
not active.

This keeps the cancellation target prominent while minimizing obstruction of
the streamed response. No ViewModel state transition changes are required.

## Validation

- Existing ViewModel tests are strengthened to assert draft preservation on
  failure and cancellation.
- Build and lint verify the Compose branch.
- Physical-device validation should confirm the smaller bottom area and the
  restored composer after cancellation or failure.
