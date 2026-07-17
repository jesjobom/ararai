# Design: Native TTS for assistant responses

## Architecture

Android `TextToSpeech` SHALL remain behind a `ChatTextToSpeechService`-style
boundary. Compose owns only playback UI state and invokes semantic operations
such as `speak(messageId, text)`, `stop()`, and `close()`; it SHALL NOT create or
control `TextToSpeech` directly.

The Android adapter initializes lazily or at Chat startup, tracks initialization
and utterance callbacks, and translates Android-specific errors into stable app
results. Tests use a fake service and require no Android speech engine.

## Playback state

At most one assistant message is the active utterance. Starting a different
message first stops the current utterance. The active message shows a stop icon;
all other eligible messages show a sound icon.

Completion, error, explicit stop, and Chat disposal all clear the active
message. Callback correlation SHALL use a unique utterance ID so a late callback
from a stopped utterance cannot clear or mutate a newer playback.

## Text selection

Only the persisted/displayed assistant response text is passed to TTS. Reasoning
is a separate field and SHALL never be concatenated into the spoken text.
Messages with blank response text or messages still being generated do not show
the action.

## Language and availability

The first version uses the device TTS engine's configured default language and
voice. ArarAI does not select a locale inferred from model output because that
would be unreliable without explicit language detection and product controls.

Android TTS availability is device-dependent and may require an installed engine
and voice data. Initialization or synthesis failure is shown as a short Chat
error. The app SHALL NOT launch installation flows automatically and SHALL NOT
claim offline availability.

## Lifecycle

Speech is foreground-only. Leaving Chat, removing the service from composition,
or destroying its owner stops the current utterance and calls `shutdown()` on
the Android TTS instance. Re-entering Chat may create a fresh instance.

## Accessibility

The sound and stop actions SHALL expose distinct localized content descriptions
so screen readers announce their current behavior.

