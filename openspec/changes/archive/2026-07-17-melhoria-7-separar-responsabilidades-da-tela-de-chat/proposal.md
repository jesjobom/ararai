# Change: Separate Chat UI and Media Responsibilities

## Why

The Chat screen currently combines Compose presentation, permission handling,
image import, audio recording, audio playback, WAV encoding, bitmap operations,
and filesystem access. This concentration makes UI changes risky and leaves
Android lifecycle and I/O behavior difficult to test independently.

## What Changes

- Split the Chat presentation into cohesive repository-standard components.
- Move image import, audio capture, playback lifecycle, and media filesystem
  operations behind focused interfaces.
- Keep Chat orchestration and durable state in the ViewModel/store boundaries.
- Preserve current UI behavior and persisted content format as a refactor invariant.
- Add characterization tests before moving behavior.

## Impact

- Cross-cutting refactor of Chat presentation and media boundaries.
- Depends on improvements 1, 4, and 8, or equivalent characterization coverage,
  before implementation begins.
- Adds no new user-facing feature by itself.
