# Clarify random item selection in widget algorithms

## Why

A physical widget-authoring session repeatedly returned `steps[].kind=custom_source` while asking for one random web result. That value belongs to `renderCapability`, not to the typed step-kind enum. The algorithm context incorrectly directed random selection into `custom_source` even though `collection_card` already supports `seeded_random` selection.

## What Changes

- State consistently in the algorithm objective, context, tool schema, and repair guidance that selecting one unfiltered item from one returned array is a collection-card presentation choice, not an extra data-operation step.
- Keep `custom_source` for actual post-result filtering, comparison, or conditional presentation; keep tool-call `selectionMode` specific to tools that support it.
- Add a regression using the rejected artifact shape and a valid declarative alternative.

## Impact

Model-facing algorithm guidance and tests only. The algorithm wire format, validator authority, persisted checkpoints, and already confirmed widgets remain unchanged.
