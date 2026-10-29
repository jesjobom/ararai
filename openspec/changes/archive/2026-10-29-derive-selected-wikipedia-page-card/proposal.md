# Derive selected Wikipedia page cards locally

## Why

A retained widget edit asking for a random Wikipedia page about “Brasil” reached the render stage but failed nine times. The model copied `defaultBodyField` from the result envelope as if it were an item field and repeatedly submitted an invalid card. `wikipedia_pages@2` already returns exactly one selected page with a complete `extract`, so no model-authored render mapping is needed.

## What Changes

- For a one-step `collection_card` algorithm using `wikipedia_pages@2`, derive title, complete extract, and canonical HTTPS link locally from `pages[0]`.
- Preserve the tool's first/seeded-random selection in the call arguments; card selection is always first because version 2 returns one selected page.
- Use the same deterministic render in resumable and single-shot authoring, leaving older contract versions and custom layouts unchanged.

## Capabilities

### Modified Capabilities

- `llm-widget-authoring`: Avoid a model-authored render stage for a directly displayed selected Wikipedia page.

## Impact

Authoring render derivation and focused tests. No tool-contract or persisted-revision migration.
