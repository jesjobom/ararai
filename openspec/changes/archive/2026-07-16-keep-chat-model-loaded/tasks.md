## 1. Specification

- [x] Define model retention across internal navigation.
- [x] Define the conditions that still require unloading.
- [x] Exclude KV-cache reuse and background retention guarantees.

## 2. Implementation

- [x] Stop unloading the Chat engine solely because the user leaves Chat.
- [x] Continue cancelling active generation when leaving Chat.
- [x] Preserve unload behavior for model replacement and unavailable/invalid
  model states.

## 3. Validation

- [x] Add focused tests for leaving and returning to Chat with an unchanged
  model.
- [x] Add or update tests for unload on model invalidation or replacement.
- [x] Run OpenSpec strict validation.
- [x] Run Android unit tests and debug APK assembly.
