# Derive a single ranked web-content card

## Why

A request for the best result from a web search can be misclassified as a custom post-result transformation. That forces a model-authored JavaScript render stage, which repeatedly exceeds the 90-second watchdog on the retained device, even though the provider has already ranked the results.

## What Changes

- Interpret an explicit best/first web-search-result request with only `web_search_content@1` as selection of the provider's highest-ranked result.
- Require one `collection_card` render capability without a post-result transform for that narrow request shape.
- Derive the fixed title, excerpt, and proven URL mapping locally; retain model-authored custom source for filtering, comparison, and other transformations.
- Allow existing accepted custom-source checkpoints to remain intact until the user explicitly reprocesses the algorithm.

## Capabilities

### Modified Capabilities

- `llm-widget-authoring`: Avoid unnecessary render generation for a single top-ranked web-content result.

## Impact

Algorithm authoring/validation, deterministic card derivation, and runtime regression tests. No migration or change to confirmed widgets.
