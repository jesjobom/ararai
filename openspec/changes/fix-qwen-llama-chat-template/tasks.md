## 1. Prompt Boundary

- [x] Add structured chat prompt messages to the local engine request boundary.
- [x] Change the chat context builder to budget and return role-tagged messages.
- [x] Update llama.cpp generation to apply the native chat template to structured messages.
- [x] Keep LiteRT-LM behavior compatible with existing text, image, and audio requests.

## 2. Qwen Runtime Defaults

- [x] Increase Qwen GGUF context size.
- [x] Align Qwen sampler defaults with the researched llama.cpp profile.
- [x] Keep Qwen GGUF entries CPU-only pending GPU validation.

## 3. Validation

- [x] Add/update unit tests for structured prompt history and native template calls.
- [x] Add/update catalog tests for Qwen defaults.
- [x] Run OpenSpec validation.
- [x] Run JVM tests.
- [x] Build debug APK and copy the handoff artifact if the build succeeds.
