## Why

Qwen3.5 GGUF models still generate degenerate repeated tokens after sampler
tuning. The remaining high-probability cause is prompt/template mismatch: the
app builds a text transcript with `System:`, `User:`, and `Assistant:` labels,
then asks llama.cpp to apply the GGUF chat template to that whole transcript as
a single user message.

Qwen3.5 expects structured chat roles and a larger context budget. The runtime
should pass system, user, and assistant messages separately to the native chat
template boundary before generation.

## What Changes

- Carry structured text chat messages through `PromptRequest` for llama.cpp.
- Apply the GGUF chat template to separate system/user/assistant messages in
  JNI instead of wrapping a preformatted transcript as one user message.
- Keep budget trimming in Kotlin, but output structured chat messages instead of
  a raw transcript for llama.cpp.
- Increase configured Qwen context and align Qwen sampler defaults with the
  researched profile.
- Add tests proving structured message roles reach the native template boundary.

## Impact

- Affects llama.cpp text generation requests.
- LiteRT-LM multimodal requests keep using the text content already prepared by
  the chat layer.
- Qwen models may use more memory because of the larger context.
