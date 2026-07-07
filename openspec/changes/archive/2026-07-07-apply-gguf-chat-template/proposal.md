# Apply GGUF Chat Template

## Why

The first real local inference smoke test produced low-quality, incoherent
responses. The configured model is small, but the runtime also currently sends
the raw user prompt directly to llama.cpp. Instruct/chat GGUF models usually
require their embedded chat template so the model receives role-formatted
`user` and `assistant` context.

Applying the GGUF chat template is the smallest technical correction before
deciding whether the configured model itself is too weak.

## What Changes

- Format generation prompts with the loaded model's GGUF chat template when one
  is available.
- Send the native runtime a role-aware user message and assistant generation
  prompt instead of raw text.
- Keep generation local and behind the existing `LocalLlmEngine` boundary.
- Preserve deterministic fake-engine tests.
- Add JVM-testable coverage that the real engine requests chat-template
  formatting before native generation.

## Out Of Scope

- Changing the configured model.
- Adding conversation history beyond the current single-turn prompt sent from
  the chat screen.
- Adding system prompt UI, prompt templates, settings, model picker, or sampling
  controls.
- Tuning generation quality beyond correcting chat prompt formatting.
