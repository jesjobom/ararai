## Why

Algorithm guidance was duplicated across the objective, tool schema, validated context, and repair text. A contradiction between those surfaces previously instructed the model to submit a render capability as a step kind, even though individual tests passed.

## What Changes

- Derive algorithm step and presentation enums from the accepted domain types.
- Route direct item, direct list, and processed-result guidance through one typed catalog, including the full initial and repair requests.
- Replace duplicated prose with compact structured policies and enforce a representative prompt-size baseline in tests.

## Scope

The model artifact and persisted widget contracts do not change. This change targets the algorithm stage, where the observed contradiction occurred; the shared pattern can be applied to other stages when their contracts change.
