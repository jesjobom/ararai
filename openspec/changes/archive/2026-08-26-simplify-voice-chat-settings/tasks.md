## 1. Simplify the Voice Chat settings hierarchy

- [x] 1.1 Add focused Compose tests proving that reasoning and reading speed are
  initially visible, every other product control is initially hidden, and the
  advanced disclosure exposes and hides those controls accessibly.
- [x] 1.2 Refactor the Voice Chat settings dialog into primary and advanced
  sections, with transient collapsed-by-default disclosure state and localized
  English/Portuguese copy.
- [x] 1.3 Verify advanced controls retain their existing values and immediate
  persistence across collapse/expand, while closing and reopening starts
  collapsed without changing product settings.

## 2. Preserve existing settings behavior

- [x] 2.1 Verify Reset restores both primary and hidden advanced defaults without
  requiring expansion or changing disclosure state.
- [x] 2.2 Run the focused Voice Chat settings tests, then
  `scripts/quality-gate.sh`; record physical-device validation as not executed
  unless it is actually performed.
