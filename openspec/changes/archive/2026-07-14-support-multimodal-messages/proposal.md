# Support Multimodal Messages

## Why

ArarAI currently treats every chat request and stored chat message as plain
text. That blocks models that can already consume image or audio inputs, and it
pushes the app toward avoidable workarounds such as transcribing audio before
generation even when the selected runtime can consume audio directly.

The model catalog is already the source of truth for runtime and artifact
metadata. It should also declare which input modalities a model/runtime
combination supports so the chat UI can show only valid controls and the engine
boundary can reject unsupported requests predictably.

## What Changes

- Replace the text-only prompt and chat message shape with structured prompt
  content that can be either text or audio, with images attached only to text
  prompts.
- Add configured model input-capability metadata for supported modalities.
- Show image and audio attachment actions only when the selected model declares
  that capability and the runtime implementation supports it.
- Support submitting text alone, text with image attachments, or audio alone
  through the chat flow.
- Wire the LiteRT-LM runtime as the first multimodal implementation by mapping
  message parts to LiteRT-LM content parts.
- Keep llama.cpp text-only until its multimodal path is implemented separately.
- Do not add audio transcription in this change.

## Impact

- Chat state, prompt building, session persistence, tests, and UI need a schema
  migration from plain text messages to structured prompt content.
- Existing text-only chat history must remain readable after migration.
- Runtime selection becomes capability-aware: multimodal controls are hidden for
  text-only models, and unsupported multimodal requests fail before inference.
- LiteRT-LM model configuration needs explicit modality metadata and runtime
  initialization must include the backends required by the declared modalities.
