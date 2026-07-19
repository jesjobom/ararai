# Change: Bound Chat streaming UI update cost

## Why

Every generated token currently snapshots growing strings, copies the message
list, triggers full-text scroll keys and reparses Markdown. Long responses can
therefore create increasing main-thread work and visible jank.

## What Changes

- Buffer engine tokens independently from structural Chat state updates.
- Publish display snapshots at a bounded cadence with immediate terminal flushes.
- Reduce full-list/full-text reactions used by scrolling and TTS preparation.
- Avoid redundant Markdown parsing for unchanged displayed text.
- Add deterministic streaming, cancellation, durability, and UI cadence tests.

## Impact

- Affected spec: `local-llm-hub`
- Affected code: Chat ViewModel buffering, Chat screen effects, Markdown rendering, tests
- Token ordering, persisted final content, and user-visible streaming: preserved
