## 1. Specification

- [x] Define model capability metadata for text, image, and audio inputs.
- [x] Define structured chat content and unsupported-runtime behavior.

## 2. Domain And Persistence

- [x] Replace text-only chat message/request models with structured text-prompt
  and audio-prompt content.
- [x] Add or migrate SQLite persistence for structured chat content.
- [x] Preserve existing text-only chat history as text-prompt content with no
  image attachments.
- [x] Add app-owned storage for selected image attachments and audio prompts.

## 3. Model Catalog

- [x] Extend model config parsing with input modality capabilities.
- [x] Default existing catalog entries to text-only unless explicitly marked
  otherwise.
- [x] Mark compatible LiteRT-LM multimodal model entries with image and audio
  support only after runtime validation.

## 4. Runtime Boundary

- [x] Update `LocalLlmEngine` and `PromptRequest` to accept structured prompt
  content.
- [x] Add runtime capability validation before generation.
- [x] Map LiteRT-LM text prompts, image attachments, and audio prompts to
  LiteRT-LM content.
- [x] Keep llama.cpp text-only and reject multimodal requests before JNI.

## 5. UI

- [x] Show image and audio actions only for the selected model capabilities.
- [x] Support text prompt submission with optional image attachments.
- [x] Support image-only prompt submission for image-capable models.
- [x] Support audio prompt submission without accompanying text or transcription.
- [x] Prevent mixed audio-plus-text draft submission.
- [x] Render user text prompts, image attachments, and audio prompts in chat
  history.
- [x] Render image thumbnails in the draft composer and chat history.
- [x] Keep text-only models free of image/audio affordances.

## 6. Validation

- [x] Normalize imported image attachments to app-owned JPEG files before
  runtime submission.
- [x] Add tests for catalog capability parsing and defaults.
- [x] Add tests for chat draft submission with text, text-plus-image, and audio.
- [x] Add tests for image-only draft submission.
- [x] Add tests proving audio-plus-text drafts cannot be submitted.
- [x] Add tests for unsupported multimodal requests on text-only runtimes.
- [x] Add/update persistence migration tests for existing text history.
- [x] Run JVM tests.
- [x] Build debug APK and copy the handoff artifact if the build succeeds.
