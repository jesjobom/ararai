# Change: Batch Persistence of Streamed Assistant Output

## Why

Every generated text delta currently concatenates the complete assistant text
and performs a SQLite update. This creates avoidable allocation and disk-write
pressure on the same path whose performance defines the local inference UX.

## What Changes

- Keep streamed UI updates responsive while buffering persistence separately.
- Persist assistant output at a bounded cadence instead of once per delta.
- Flush pending content on completion, cancellation, controlled failure, and
  lifecycle transitions where durability is required.
- Preserve partial assistant output when generation is interrupted.
- Add deterministic tests for batching and every final flush path.

## Impact

- Touches Chat generation state and message persistence coordination.
- Does not change model output, stored content format, or visible streaming cadence.
- Must preserve the current cancellation and partial-response behavior.
