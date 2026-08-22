## Why

`ArarAiApp.kt` remains a high-churn 2,011-line UI hub containing navigation and
multiple complete destinations, with complexity suppressions hiding the
concentration. Earlier Chat extraction showed that characterization-first,
destination-sized changes can reduce collision risk without rewriting navigation.

## What Changes

- Characterize destination behavior and extract one cohesive destination at a
  time from `ArarAiApp.kt`.
- Keep navigation and controller ownership stable unless a specific extraction
  proves a smaller boundary is required.
- Remove complexity suppressions only as their underlying cause disappears; do
  not bundle visual or product behavior changes.

## Capabilities

### New Capabilities

None. This is a behavior-preserving structural change.

### Modified Capabilities

None.

## Impact

- Affected code: application shell and destination composables, previews/tests,
  and architecture documentation.
- Risk: broad simultaneous extraction would create review and regression risk,
  so work is explicitly incremental.
