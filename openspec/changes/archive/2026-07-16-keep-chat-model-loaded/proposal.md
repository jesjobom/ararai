# Change: Keep Chat Model Loaded Across Internal Navigation

## Why

Leaving Chat for another screen currently unloads the selected model even when
the model has not changed. Returning to Chat then incurs the full model-load
cost before the next message, adding avoidable latency during normal use.

## What Changes

- Keep the Chat model engine loaded when the user leaves Chat through internal
  app navigation.
- Cancel an active generation when leaving Chat without unloading unchanged
  model weights.
- Continue unloading when the selected model changes, becomes unavailable,
  invalid, deleted, or otherwise cannot remain active.
- Preserve the existing idempotent load behavior so the next Chat request
  reuses the already-loaded matching model.

## Out of Scope

- Reusing a conversation or KV cache between generations.
- A timed background-unload policy.
- New Android memory-pressure or process-lifecycle handling.
- Guaranteeing retention after Android kills the app process.

## Impact

- Touches Chat navigation/lifecycle behavior and focused ChatViewModel tests.
- Improves return-to-Chat latency at the cost of retaining the selected model's
  RAM/GPU resources during internal navigation.
