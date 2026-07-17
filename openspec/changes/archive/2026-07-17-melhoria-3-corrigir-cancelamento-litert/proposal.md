# Change: Close Retained LiteRT-LM Conversations on Cancellation

## Why

LiteRT-LM cancellation invalidates retained conversation state but does not
consistently close the native conversation it discards. Repeated cancellation
can leave native, CPU, or GPU resources without a reachable owner.

## What Changes

- Define explicit ownership for active and retained LiteRT-LM conversations.
- Cancel and close invalidated conversations exactly once.
- Clear all references after cancellation, error, profile change, model change,
  or unload.
- Preserve safe reuse only after successful compatible generation.
- Add lifecycle tests for cancellation followed by generation and unload.

## Impact

- Touches only LiteRT-LM conversation lifecycle and focused tests.
- Does not alter the generic engine API or successful conversation-reuse rules.
- Should be completed before adding further retained native state.
