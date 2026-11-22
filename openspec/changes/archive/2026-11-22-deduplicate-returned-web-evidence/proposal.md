# Deduplicate evidence that is actually returned

## Why

The shared Chat and widget search normalizer can reserve a URL, excerpt, or
source slot for a candidate that contributes nothing to the result. This can
hide usable evidence or incorrectly return no results. The provider guide
also shows fields the model-facing contract does not accept.

## What changes

- Count only sources with evidence that fits and deduplicate against included
  evidence, retaining provider ranking and the existing Chat and widget limits.
- Document the existing query-only model-facing contract.
