# Tasks

## Proposal

- [x] Create OpenSpec proposal for applying the GGUF chat template.

## Tests First

- [x] Add a failing JVM test that the real engine formats prompts through the
      native chat-template bridge before generation.
- [x] Preserve existing fake-engine and chat ViewModel tests.

## Implementation

- [x] Extend the native bridge contract with chat prompt formatting.
- [x] Implement GGUF template formatting with `llama_model_chat_template` and
      `llama_chat_apply_template`.
- [x] Use the formatted prompt for native tokenization/generation.
- [x] Fall back safely to the raw prompt only when the model has no usable chat
      template.

## Validation

- [x] Run `./gradlew testDebugUnitTest`.
- [x] Run `./gradlew assembleDebug`.
- [x] Run `openspec validate apply-gguf-chat-template --strict`.
- [x] Copy the debug APK to
      `/home/node/.openclaw/jarvis/artifacts/ararai/app-debug.apk`.
