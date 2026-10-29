# Advance widget authorship in one tap

## Why

After a stage succeeds, the authoring UI requires one Avançar tap to accept it and a second Avançar tap to start the next stage. Both operations are one intended continuation and need not demand separate gestures.

## What Changes

- A single Avançar tap on a successfully reviewed stage accepts that artifact and starts its next stage when the authoring model configuration is available.
- Keep explicit review between stages: completing a generation does not automatically accept its result.
- Stop at feasibility rejection or the final render preview, and never start another attempt after a stale/non-applied acceptance.
- If model configuration becomes unavailable, preserve the accepted checkpoint and allow the next stage to be started when configuration returns.

## Capabilities

### Modified Capabilities

- `llm-widget-authoring`: Combine user-requested acceptance and next-stage start while preserving durable checkpoints and single-flight execution.

## Impact

Widget authoring coordinator and Compose flow, plus deterministic workflow tests. No widget program, repository schema, or confirmed revision changes.
