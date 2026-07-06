# Tasks

## Proposal

- [x] Create OpenSpec proposal for home and model status navigation.

## Tests First

- [x] Add failing tests for model status UI-state mapping.
- [x] Add failing test that retry is exposed only for failed model state.
- [x] Add failing test for progress percent formatting.

## Implementation

- [x] Add minimal app destination state for `Home` and `ModelStatus`.
- [x] Implement home screen with one `Model status` action.
- [x] Implement model status screen with model name, status, progress, retry,
      and back action.
- [x] Wire `MainActivity` to start at home and collect model startup state.
- [x] Remove chat surface from the first visible flow for this change.

## Validation

- [x] Run `./gradlew testDebugUnitTest`.
- [x] Run `./gradlew assembleDebug`.
- [x] Run `openspec validate add-home-and-model-status-navigation --strict`.
- [x] Copy the debug APK to
      `/home/node/.openclaw/jarvis/artifacts/ararai/app-debug.apk`.
