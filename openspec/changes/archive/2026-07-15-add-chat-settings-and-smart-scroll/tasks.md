## 1. Specification

- [x] Define Chat settings overlay behavior for reasoning controls.
- [x] Define Chat message list auto-scroll behavior.

## 2. Implementation

- [x] Add Chat settings UI entry point and overlay modeled after the session
  list surface.
- [x] Add explicit catalog/runtime metadata for reasoning request and reasoning
  output support.
- [x] Add Chat UI state and handlers for enabling reasoning and showing
  reasoning.
- [x] Extend the chat generation request boundary so reasoning can be requested
  when the selected runtime supports it.
- [x] Render reasoning content only when available and `Show reasoning` is
  enabled.
- [x] Implement bottom-aware auto-scroll for Chat entry, session switching,
  user submissions, and streamed assistant output.

## 3. Validation

- [x] Add or update focused unit tests for reasoning settings state.
- [x] Add or update focused UI/view-model tests for auto-scroll decisions where
  practical.
- [x] Run OpenSpec validation.
- [x] Run relevant Android unit/build validation.
