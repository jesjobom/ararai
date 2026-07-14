## Why

GGUF models can generate degenerate repeated tokens when the prompt/template
boundary is wrong. The high-probability cause is prompt/template mismatch: the
app builds a text transcript with `System:`, `User:`, and `Assistant:` labels,
then asks llama.cpp to apply the GGUF chat template to that whole transcript as
a single user message.

Chat-tuned GGUF models expect structured chat roles. The runtime should pass
system, user, and assistant messages separately to the native chat template
boundary before generation.

## What Changes

- Carry structured text chat messages through `PromptRequest` for llama.cpp.
- Apply the GGUF chat template to separate system/user/assistant messages in
  JNI instead of wrapping a preformatted transcript as one user message.
- Keep budget trimming in Kotlin, but output structured chat messages instead of
  a raw transcript for llama.cpp.
- Add tests proving structured message roles reach the native template boundary.

## Impact

- Affects llama.cpp text generation requests.
- LiteRT-LM multimodal requests keep using the text content already prepared by
  the chat layer.
