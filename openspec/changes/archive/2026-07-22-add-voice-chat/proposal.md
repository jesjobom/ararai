## Why

ArarAI's current Chat supports isolated audio prompts and optional speech playback,
but it still requires interaction with the text-chat workflow. A dedicated voice
experience can make conversation hands-free and reduce perceived latency by
turning natural pauses into user turns and speaking the response incrementally.

## What Changes

- Add a dedicated voice-chat screen whose initial state is centered on one large
  action to start the conversation. While a conversation is active, the same
  action stops the conversation and immediately ceases microphone capture.
- Add an action on the voice-chat screen to open settings and experimental
  diagnostics specific to this experience.
- Let the user configure how long a pause must last before the captured speech
  is treated as a complete turn and submitted for processing.
- Let the user configure the minimum number of generated response words that
  must be accumulated before a segment is converted to speech. Any remaining
  words at the end of a response are still spoken even when they do not reach
  that threshold.
- After the conversation starts, continuously capture the user's speech and use
  the configured pause duration to delimit each user turn.
- Submit each completed audio turn to the conversational model while preserving
  the context accumulated during that voice conversation.
- Convert the model's streamed response into speech in independently playable
  segments, so playback can begin before the full response has completed.
- Present clear listening, processing, and speaking states, with a way to end
  the voice conversation.
- Present the generated answer in a persistent two-line reading viewport that
  follows and highlights native TTS progress, and open the complete answer in
  an expanded overlay when the viewport is activated.
- Keep version 0 stateless: each audio turn is processed without earlier turns,
  no Voice Chat sessions or history are stored, and temporary recordings are
  deleted after use.
- Compare offline voice-activity detection, supported Android capture sources,
  and optional native noise suppression through local experimental controls and
  metrics before selecting the production audio pipeline.

The accompanying specification and design define setting bounds, the initial
half-duplex interaction model, response segmentation, ephemeral media lifecycle,
local diagnostics, and controlled failure behavior. Direct audio remains the
model input. Speech transcription, conversational history/context, sessions,
and user interruption while the assistant is speaking are not part of version
0. Acoustic echo cancellation remains a candidate for a future full-duplex or
barge-in change rather than being rejected by this experiment.

## Capabilities

### New Capabilities

- `voice-chat`: Dedicated stateless voice turns, including configurable
  pause-based turn detection, experimental capture/VAD diagnostics, incremental
  speech playback, lifecycle controls, and settings.

### Modified Capabilities

- None in this initial proposal. Existing text Chat, recorded audio prompts, and
  manual TTS playback retain their current contracts.

## Impact

- Affected areas: home/navigation, a new Compose screen and state owner,
  microphone capture and turn detection, conversational inference streaming,
  segmented TTS playback, voice-chat preferences and local metrics, permissions,
  temporary media cleanup, and lifecycle/resource cleanup.
- Likely dependencies: existing local model runtime and Android audio/TTS
  boundaries; whether additional on-device voice processing is required remains
  a design decision.
- Privacy direction: voice conversations remain local-first and must not imply a
  hosted speech or inference service without a future explicit scope change.
- Compatibility: version 0 introduces no Voice Chat database or session
  migration. No breaking change is proposed for existing Chat sessions, audio
  prompts, model management, or manual response playback.
