# Change: Include Textual Context with Audio Prompts

## Why

Audio prompts currently reach the LiteRT-LM runtime without the configured
system prompt or the selected session's prior textual conversation. This makes
an audio turn behave like an isolated request and breaks conversational
continuity compared with text and image turns.

## What Changes

- Build the same bounded textual context for an audio turn that Chat uses for
  other prompts: system instruction plus recent session history.
- Send that textual context together with the current audio file to the
  multimodal runtime.
- Preserve message roles and context-budget trimming.
- Keep the persisted audio message and its playback metadata unchanged.

## Out of Scope

- Re-sending historical image or audio files to the model.
- Transcribing audio before generation.
- Supporting audio on runtimes or models that do not declare audio input
  capability.
- Reusing a conversation or KV cache between requests.

## Impact

- Touches prompt-context construction, the structured generation request, the
  LiteRT-LM adapter, and focused context/runtime tests.
- Increases audio-turn prefill work because relevant textual history will now
  accompany the audio input.
