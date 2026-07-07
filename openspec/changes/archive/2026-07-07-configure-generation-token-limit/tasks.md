# Tasks

## Proposal

- [x] Create OpenSpec proposal for configurable generation token limit.

## Tests First

- [x] Add failing parser tests for `inference.maxTokens`.
- [x] Add failing engine tests that native generation receives the configured
      max-token value.

## Implementation

- [x] Add `maxTokens` to `InferenceConfig`.
- [x] Parse and validate `inference.maxTokens`.
- [x] Set the fixed model default to a larger value.
- [x] Use the configured value in `LlamaCppLocalLlmEngine` instead of the
      hardcoded default.

## Validation

- [x] Run `./gradlew testDebugUnitTest`.
- [x] Run `./gradlew assembleDebug`.
- [x] Run `openspec validate configure-generation-token-limit --strict`.
- [x] Copy the debug APK to
      `/home/node/.openclaw/jarvis/artifacts/ararai/app-debug.apk`.
