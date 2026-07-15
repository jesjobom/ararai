# Change: Add Chat Settings and Smart Scroll

## Why

Chat needs a small configuration surface for generation behavior without
overloading the composer or top bar. Reasoning controls are the first options:
one toggle to request reasoning from capable models, and another to decide
whether reasoning content is visible in the conversation.

The chat message list also currently opens at the oldest content. Daily use
needs the opposite default: entering Chat, switching sessions, sending messages,
and receiving streamed output should keep the newest messages visible when the
user is already following the bottom of the conversation. If the user scrolls
up to inspect earlier content, the app should respect that and stop forcing the
list downward.

## What Changes

- Add a Chat settings overlay, visually similar to the existing session list
  overlay, with room for future chat options.
- Add `Enable reasoning` and `Show reasoning` controls to that overlay.
- Add explicit catalog metadata for model/runtime reasoning support so unsupported
  models do not receive reasoning request options.
- Thread the reasoning settings through chat state and generation requests in a
  way that can no-op safely for runtimes or models that do not support
  reasoning yet.
- Track whether the message list is at the bottom and auto-scroll only when the
  user is following the latest content.
- Scroll to the latest message when entering Chat or switching to an existing
  session.

## Impact

- Touches Chat UI, ChatViewModel state, model catalog metadata,
  prompt/generation request boundaries, and focused UI/view-model tests.
- Current catalog entries can opt into reasoning support explicitly when JJ wants
  to validate that model/runtime pair on device.
- Does not require exposing reasoning when a runtime does not return structured
  reasoning content.
