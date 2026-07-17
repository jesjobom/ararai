# Change: Add native TTS playback to Chat responses

## Why

Users can read assistant responses but cannot listen to them. Android already
provides a native text-to-speech boundary that can offer simple foreground
playback without adding a third-party speech dependency.

## What Changes

- Add a sound action to completed assistant messages that contain response text.
- Use the device's default Android TTS engine, language, and voice.
- Replace the sound action with a stop action while that response is speaking.
- Allow only one response to speak at a time and stop the previous response
  before starting another.
- Never send reasoning content to TTS.
- Stop and release TTS resources when Chat leaves composition.
- Surface initialization, language, voice-data, and playback failures without
  crashing or redirecting the user to an installer.
- Isolate Android `TextToSpeech` behind a testable service boundary.

## Out of Scope

- Voice, language, speed, or pitch selection
- Pause and resume
- Background or lock-screen playback
- Automatic reading of streamed or newly completed responses
- Installing or downloading a TTS engine or voice data

## Impact

- Affected spec: `local-llm-hub`
- Affected code: Chat message actions, Chat media/services boundary, Android TTS
  adapter, and lifecycle handling
- Dependencies: no new third-party runtime dependency
- Compatibility: no database or persisted message format change

