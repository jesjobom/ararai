# Change: Record Audio Prompts

## Why

Chat audio input currently requires choosing an existing audio file. That makes
quick voice prompts awkward, especially on mobile where the natural workflow is
to speak into the current chat and send the captured audio immediately.

When the selected model/runtime supports audio input, the composer should offer
an in-place recording flow that produces the same `AudioPrompt` shape already
used by file-selected audio.

## What Changes

- Add microphone recording from the Chat composer when audio input is supported.
- Request microphone permission only when the user starts the recording flow.
- Store recordings in app-owned chat media storage and attach the recorded file
  as an `AudioPrompt`.
- Start recording immediately when the user taps the composer audio action.
- Let the user replay the captured audio in the review dialog before sending it.
- Let persisted audio prompt messages be replayed from chat history.

## Impact

- Touches Chat UI, Android permissions, local media file creation, and focused
  tests around audio prompt metadata where practical.
- Reuses existing chat persistence and runtime audio request handling.
- Removes the audio file picker path from the Chat composer.
- Does not change model catalog selection or download behavior.
