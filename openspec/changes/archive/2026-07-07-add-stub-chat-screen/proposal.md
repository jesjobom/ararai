# Add Stub Chat Screen

## Why

The app has a home screen and model status flow, but chat is not currently
exposed from the main navigation. Before integrating llama.cpp, the product
needs a real chat screen that validates the conversation UX against the existing
fake local inference engine boundary.

## What Changes

- Add a `Chat` destination reachable from `Home`.
- Add a home action to open chat while keeping the model status action.
- Add a dedicated Compose chat screen with message list, prompt input, send
  action, generating state, error display, and a back button.
- Keep the chat backed by the fake/stub `LocalLlmEngine`.
- Disable prompt submission while the model is unavailable or generation is in
  progress.
- Preserve the existing model startup/download behavior and model status screen.

## Out Of Scope

- Native llama.cpp/JNI integration.
- Downloading a new real LLM model.
- Model picker, chat history persistence, multiple conversations, attachments,
  voice, image, settings, or prompt templates.
- Streaming performance tuning beyond the fake engine behavior already used by
  tests.
