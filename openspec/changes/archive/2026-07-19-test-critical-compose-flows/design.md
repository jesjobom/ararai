# Design: Semantics-driven Compose journey coverage

## Context

`ArarAiApp` coordinates Home, Chat, Models, Diagnostics, and Settings. `ChatScreen`
coordinates dialogs, streaming controls, scrolling, retry, and TTS. Existing
instrumentation primarily validates platform boundaries rather than user actions.

## Decisions

Use Compose testing APIs with stable content descriptions/test tags only where
visible text or roles are insufficient. Test behavior and navigation state, not
layout coordinates or screenshots.

Create deterministic fakes for model state, local generation, persistence, TTS,
language identification, image import, and audio. Split JVM/Robolectric versus
device Compose tests according to the narrowest reliable layer, and keep real
runtime/device behavior in the physical matrix.

## Validation

- Cover Home-to-Chat navigation, unavailable-model retry, submit/cancel,
  session rename/delete, and theme selection.
- Run tests repeatedly to detect ordering/timing flakes before adding them to CI.
- The common quality gate remains the single automated entry point.
