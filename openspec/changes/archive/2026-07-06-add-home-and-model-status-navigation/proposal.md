# Add Home And Model Status Navigation

## Why

The app currently opens directly into a debug chat surface. Before adding more
features, the UI needs a small app-level structure that can grow into a hub.
The next step is a home screen with one action that opens model download/status
details. Chat should not be exposed in this change.

## What Changes

- Add a simple in-app destination model with `Home` and `ModelStatus`.
- Start the app on `Home`.
- Add one home action for opening the model status screen.
- Move model availability/download/progress/retry presentation into the
  `ModelStatus` screen.
- Keep model download startup behavior active.
- Hide the chat surface for now.
- Add focused tests for model status UI-state mapping.

## Out Of Scope

- Chat route or chat entry point.
- Multi-feature dashboard content beyond the model status action.
- Bottom navigation, drawer navigation, or deep links.
- Settings, model picker, voice, image, history, or native llama.cpp inference.
- Full design system or app icon work.
