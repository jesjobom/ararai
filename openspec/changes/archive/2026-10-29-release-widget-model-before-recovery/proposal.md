# Release the shared model before widget recovery

## Why

A render retry can wait for ten minutes and fail its memory gate while the shared LiteRT-LM engine still owns a multi-gigabyte GPU session. The stage runner checks memory before releasing that session, contrary to the existing authoring recovery contract.

## What Changes

- Explicitly release any shared model session before entering the device-state recovery gate for a model-authored stage or retry.
- Fail the attempt promptly with a controlled runtime outcome if that release fails; do not begin generation.
- Keep deterministic stages and invalid local requests free of model lifecycle transitions.

## Capabilities

### Modified Capabilities

- `llm-widget-authoring`: Ensure the engine is unloaded while awaiting recovery, even when a previous Chat or authoring session left it loaded.

## Impact

Authoring stage runner and its lifecycle regression tests. No persisted widget schema or confirmed widget behavior changes.
