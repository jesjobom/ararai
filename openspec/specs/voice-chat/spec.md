# voice-chat Specification

## Purpose
TBD - created by archiving change add-voice-chat. Update Purpose after archive.
## Requirements
### Requirement: Voice Chat destination

The app SHALL provide a dedicated Voice Chat destination with one large central
start/stop control and Voice Chat settings. The home screen SHALL present Chat
and Voice Chat as visually consistent, fully clickable conversation cards.

#### Scenario: Open Voice Chat

- **GIVEN** the user is on the home screen
- **WHEN** the user activates any part of the Voice Chat card
- **THEN** the idle screen centers a large action for starting the loop
- **AND** settings are available
- **AND** no Voice Chat session or conversation history is displayed.

#### Scenario: Distinguish conversation and utility destinations

- **WHEN** the home screen is displayed
- **THEN** Chat and Voice Chat use the same color scheme
- **AND** Models, Diagnostics, and Settings use the Model Management color scheme
- **AND** every destination card is clickable without an internal action button.

#### Scenario: Present selected-model capabilities

- **GIVEN** a model is selected
- **WHEN** the user views the Home or Models screen
- **THEN** the model's supported text, voice, image, unified reasoning, and acceleration capabilities are presented as non-interactive badges
- **AND** the local model file path is not presented
- **AND** model management shows the configured approximate download size, or the current installed size when available
- **AND** model management shows the catalog's recommended free RAM for the model
- **AND** the Home identifies model management as `Model Manager`.

#### Scenario: Selected model cannot accept audio

- **GIVEN** the selected model is unavailable or does not declare audio input
  support
- **WHEN** Voice Chat is displayed
- **THEN** the start action is unavailable
- **AND** the screen explains that a locally available audio-capable model is
  required
- **AND** it provides a path to model management.

#### Scenario: Prepare the selected model before starting

- **GIVEN** the selected model is locally available and accepts audio
- **WHEN** Voice Chat is opened
- **THEN** the app loads the selected model and prepares its audio workload
- **AND** the start action remains disabled until both operations complete
- **AND** the first captured turn does not trigger an audio-profile reload while
  presenting the thinking state.

### Requirement: Stateless half-duplex voice loop

Voice Chat version 0 SHALL process each user turn independently and SHALL keep
microphone capture inactive while the model is processing or TTS is speaking.

#### Scenario: Start the voice loop

- **GIVEN** an audio-capable selected model is locally available
- **AND** Voice Chat is idle
- **WHEN** the user activates the central control
- **THEN** the app requests microphone permission if needed
- **AND** starts app-owned audio capture after permission is granted
- **AND** presents the listening state
- **AND** changes the central control to a stop action.

#### Scenario: Process one independent turn

- **GIVEN** a valid audio turn has completed
- **WHEN** Voice Chat invokes local inference
- **THEN** the request contains the configured system prompt and current direct
  audio only
- **AND** it contains no previous Voice Chat user or assistant turn
- **AND** no speech-to-text service is invoked
- **AND** no Voice Chat session or message is persisted.

#### Scenario: Resume listening after one exchange

- **GIVEN** current generation and every queued speech segment have completed
- **AND** the loop has not been stopped
- **WHEN** the independent exchange finishes
- **THEN** the temporary audio is deleted
- **AND** a fresh recording starts
- **AND** Voice Chat returns to listening without conversational context.

### Requirement: Central cancellation and lifecycle cleanup

The central stop action and foreground-owner disposal SHALL terminate all active
Voice Chat version 0 work and prevent stale callbacks from restarting it.

#### Scenario: Stop while listening

- **WHEN** the user activates stop during recording
- **THEN** microphone capture stops promptly
- **AND** the draft recording is deleted
- **AND** no generation starts from that recording
- **AND** Voice Chat returns to idle.

#### Scenario: Stop while processing or speaking

- **WHEN** the user activates stop during generation or speech
- **THEN** generation is canceled
- **AND** active and queued speech stops
- **AND** temporary audio is deleted
- **AND** stale callbacks cannot restart recording or playback
- **AND** Voice Chat returns to idle.

#### Scenario: Leave Voice Chat during active work

- **WHEN** the user leaves Voice Chat or its foreground owner is destroyed
- **THEN** recording, generation, language preparation, and speech stop
- **AND** platform audio and TTS resources are released
- **AND** temporary audio and in-memory diagnostic records are cleared.

#### Scenario: Microphone permission is denied

- **WHEN** microphone permission is denied or revoked
- **THEN** capture does not start or continue
- **AND** Voice Chat reports a controlled permission state
- **AND** navigation and settings remain usable.

### Requirement: Offline configurable voice-activity detection

Voice Chat version 0 SHALL detect speech and trailing pauses locally from the
16 kHz mono PCM stream through a replaceable VAD boundary.

#### Scenario: Compare VAD providers

- **WHEN** the user opens experimental diagnostics while the loop is idle
- **THEN** WebRTC VAD and Silero VAD are available as offline experimental
  providers
- **AND** Silero VAD in aggressive mode is the experimental test default
- **AND** the default remains subject to physical comparison before a production
  choice
- **AND** changing provider takes effect only after the next loop start.

#### Scenario: Sustained pause after speech

- **GIVEN** the selected VAD has detected speech
- **WHEN** it reports non-speech continuously for the configured pause duration
- **THEN** the recorder finalizes the current PCM WAV
- **AND** Voice Chat submits it as the current direct-audio turn
- **AND** enters processing.

#### Scenario: Confirm usable speech before submission

- **WHEN** candidate speech does not satisfy the configured confirmation and
  minimum-speech durations
- **THEN** no turn is submitted
- **AND** Voice Chat continues listening.

#### Scenario: Retain bounded audio before confirmed speech

- **WHEN** candidate speech becomes confirmed
- **THEN** the recording contains only the configured bounded pre-roll before
  that candidate and subsequent turn audio
- **AND** unbounded leading silence is not submitted to the model.

#### Scenario: Silence before speech

- **GIVEN** no speech has been detected in the current recording
- **WHEN** silence lasts longer than the configured pause duration
- **THEN** no turn is submitted
- **AND** Voice Chat remains listening.

#### Scenario: Empty or unusable capture

- **WHEN** finalization finds no usable speech audio
- **THEN** the draft is deleted
- **AND** the model is not invoked
- **AND** Voice Chat continues listening.

### Requirement: Experimental Android capture preprocessing

Voice Chat version 0 SHALL allow physical tests to compare supported Android
capture sources and optional native noise suppression without making either a
permanent product dependency.

#### Scenario: Select a capture source

- **WHEN** experimental audio settings are displayed while the loop is idle
- **THEN** the user can select `MIC`, `VOICE_RECOGNITION`, or
  `VOICE_COMMUNICATION`
- **AND** the selected source applies on the next loop start
- **AND** the diagnostics report the effective source.

#### Scenario: Enable available native noise suppression

- **GIVEN** Android reports native noise suppression as available for the active
  audio session
- **WHEN** the experimental noise-suppression option is enabled
- **THEN** the app attaches and enables `NoiseSuppressor` for that recorder
- **AND** reports whether the effect actually became active.

#### Scenario: Noise suppression is unavailable

- **GIVEN** native noise suppression is unavailable or cannot be enabled
- **WHEN** the user starts the loop with the option requested
- **THEN** recording continues without that effect
- **AND** diagnostics report that suppression was not effective
- **AND** the app does not claim that audio was filtered.

#### Scenario: Do not enable deferred effects

- **WHEN** Voice Chat version 0 configures capture
- **THEN** it does not explicitly enable automatic gain control
- **AND** it does not enable acoustic echo cancellation
- **AND** its audio-effect boundary remains extensible for a future full-duplex
  interruption experiment.

### Requirement: Incremental ordered response speech

Voice Chat SHALL convert only streamed final-answer text into ordered speech
segments and begin playback before the full response completes whenever a
segment becomes eligible.

#### Scenario: Follow the currently spoken response text

- **GIVEN** Voice Chat is speaking a generated response
- **WHEN** the native TTS engine reports the source range it is about to speak
- **THEN** the screen highlights the corresponding word or text range
- **AND** a fixed two-line response viewport follows that range
- **AND** an engine that omits precise ranges falls back to highlighting and
  following the active speech segment.

#### Scenario: Inspect the complete response while speech continues

- **GIVEN** the response viewport contains generated text
- **WHEN** the user activates it
- **THEN** a scrollable expanded overlay displays the complete current response
- **AND** the active spoken range remains highlighted and visible
- **AND** opening or closing the overlay does not stop or restart TTS.

#### Scenario: Preserve the final reading position

- **WHEN** speech playback completes
- **THEN** the active highlight is cleared
- **AND** the two-line viewport continues to display the final response lines
- **AND** the response is cleared only when the next voice response begins or
  the Voice Chat foreground owner is disposed.

#### Scenario: Accumulate an eligible segment

- **GIVEN** final-answer text is streaming
- **WHEN** unspoken complete words reach the configured minimum
- **THEN** Voice Chat waits for a sentence-ending punctuation mark or line break
- **AND** short adjacent sentences may be grouped until the minimum is reached
- **AND** normal incremental playback does not split an unfinished sentence at
  an arbitrary word boundary
- **AND** local language preparation and TTS preserve segment order.

#### Scenario: Bound an exceptionally long unfinished sentence

- **GIVEN** streamed text has no natural segment boundary within 500 source
  characters
- **WHEN** retaining more text could exceed that limit
- **THEN** Voice Chat splits at the nearest preceding clause punctuation when
  possible
- **AND** otherwise splits at a preceding word boundary
- **AND** no submitted speech segment exceeds the configured safe limit.

#### Scenario: Keep one language throughout a streamed response

- **GIVEN** the first eligible response segment has been language-identified
- **WHEN** later segments contain names, technical terms, or foreign words
- **THEN** every segment in that response uses the language selected for the
  first segment
- **AND** language identification resets before the next voice turn.

#### Scenario: Flush residual text

- **GIVEN** generation completes with non-blank unspoken text below the word
  minimum
- **WHEN** the stream completes
- **THEN** all residual final-answer text is spoken
- **AND** no words are discarded solely because they are below the minimum.

#### Scenario: Exclude reasoning and formatting controls

- **WHEN** generation emits reasoning or speech-normalizable formatting
- **THEN** reasoning is never queued for speech
- **AND** formatting is normalized for speech.

#### Scenario: Speech preparation or playback fails

- **WHEN** language identification or native TTS cannot prepare or play a segment
- **THEN** active and queued speech stops
- **AND** Voice Chat deletes temporary audio
- **AND** reports a recoverable error
- **AND** does not persist the generated response.

### Requirement: Persistent product settings

Voice Chat SHALL locally persist validated product settings for pause duration,
minimum response words, TTS speech rate, VAD mode, speech-confirmation duration,
pre-roll, and minimum usable speech duration.

#### Scenario: Configure pause duration

- **WHEN** the user selects 500 through 5,000 milliseconds in 250-millisecond
  increments
- **THEN** the value is persisted locally
- **AND** applies from the next listening cycle
- **AND** defaults to 1,500 milliseconds when no valid value is stored.

#### Scenario: Configure minimum response words

- **WHEN** the user selects 1 through 100 words
- **THEN** the value is persisted locally
- **AND** applies from the next response
- **AND** defaults to 25 words when no valid value is stored.

#### Scenario: Configure TTS speech rate

- **WHEN** the user selects a speech-rate multiplier from 0.5x through 2.0x in
  0.1x increments
- **THEN** the value is persisted locally
- **AND** applies to subsequent Voice Chat speech segments
- **AND** defaults to 1.0x when no valid value is stored.

#### Scenario: Present compact Voice Chat settings

- **WHEN** the user opens Voice Chat settings
- **THEN** speech rate is the first control
- **AND** the pause control is labelled `Pause before answer` and explains that
  it measures trailing silence before submission
- **AND** VAD provider, VAD sensitivity, and capture source use dropdown controls.

#### Scenario: Restore invalid settings

- **WHEN** a stored product setting is missing, corrupt, or outside its range
- **THEN** that setting uses its defined default
- **AND** Voice Chat remains usable.

### Requirement: Local ephemeral experiment diagnostics

Voice Chat version 0 SHALL expose bounded local measurements for comparing the
effective audio/VAD pipeline without retaining audio or conversational content.

#### Scenario: Measure an exchange

- **WHEN** a voice exchange starts, completes, fails, or is canceled
- **THEN** diagnostics record bounded timing events for speech/pause detection,
  audio finalization, inference, first generated output, first TTS, and completion
- **AND** record the selected VAD, effective audio source/effects, coarse level
  summaries, and failure category
- **AND** do not record PCM, prompt text, response text, or a stable user ID.

#### Scenario: Keep diagnostics out of the product screen

- **WHEN** Voice Chat is displayed
- **THEN** raw diagnostic counts, timings, and reset controls are not displayed
- **AND** bounded in-memory measurements remain available to automated and
  physical engineering validation.

### Requirement: Temporary local audio ownership

Voice Chat version 0 SHALL keep direct-audio processing local and SHALL delete
temporary recordings after their current exchange no longer needs them.

#### Scenario: Complete, fail, or cancel an exchange

- **WHEN** the current exchange completes, fails, or is canceled
- **THEN** its temporary WAV is deleted idempotently
- **AND** no hosted transcription, speech, inference, database, or media service
  is required.

#### Scenario: Reconcile stale temporary audio

- **GIVEN** abnormal process death left an unreferenced Voice Chat temporary file
- **WHEN** Voice Chat temporary storage is next initialized
- **THEN** stale files are removed without deleting Chat-owned media.
