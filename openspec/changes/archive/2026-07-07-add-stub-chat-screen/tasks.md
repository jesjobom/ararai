# Tasks

## Proposal

- [x] Create OpenSpec proposal for the stub-backed chat screen.

## Tests First

- [x] Add or update unit tests for chat UI-state behavior around send-enabled
      conditions.
- [x] Add or update tests that the fake engine appends deterministic assistant
      output and surfaces failures.

## Implementation

- [x] Add `Chat` to the app destination model.
- [x] Add a `Home` action that opens the chat screen.
- [x] Wire the chat screen to the existing fake/stub `LocalLlmEngine`.
- [x] Add a visible back button from chat to home.
- [x] Render conversation messages, prompt input, send action, generating state,
      model availability status, and error state.
- [x] Disable send for blank prompt, unavailable model, or in-progress
      generation.

## Validation

- [x] Run `./gradlew testDebugUnitTest`.
- [x] Run `./gradlew assembleDebug`.
- [x] Run `openspec validate add-stub-chat-screen --strict`.
- [x] Copy the debug APK to
      `/home/node/.openclaw/jarvis/artifacts/ararai/app-debug.apk`.
