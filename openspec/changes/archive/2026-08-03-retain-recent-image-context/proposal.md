# Change: Retain recent image context in Chat

## Why

Image-only turns keep the placeholder session title, and a textual follow-up
switches LiteRT-LM back to a text-only workload, discarding the visual context.

## What Changes

- Title an image-only first turn from its default image-description prompt.
- Reattach the most recent historical image set to a textual follow-up that has
  no new image, so the visual workload and image context remain available.
- Prefer explicitly attached current images over historical images.

## Impact

- Affected specs: `local-llm-hub`
- Affected code: Chat title generation, context projection, and unit tests.
