## ADDED Requirements

### Requirement: Voice Chat Photo Capture

Voice Chat SHALL let the user open an in-app camera during listening without
interrupting microphone capture or VAD. Opening or closing the camera and
completing a manual capture SHALL restart only the trailing-silence window while
preserving prior audio and confirmed speech. A valid pause SHALL automatically
capture the current frame when no manual photo is pending. Voice Chat SHALL
associate at most one photo with the current completed audio turn and SHALL NOT
reopen the camera for the next turn.

#### Scenario: Show the Voice Chat camera action

- **GIVEN** voice input can be routed for the selected model
- **AND** the selected model and runtime support image input
- **WHEN** Voice Chat is listening
- **THEN** a camera action is available
- **AND** the action remains usable while audio recording is active.

#### Scenario: Hide the Voice Chat camera action

- **GIVEN** the selected model or runtime cannot accept an image with the routed
  voice turn
- **WHEN** Voice Chat is displayed
- **THEN** the camera action is not available.

#### Scenario: Capture a photo while listening

- **GIVEN** Voice Chat is recording the current turn
- **WHEN** the user opens the in-app camera
- **THEN** microphone capture continues without being stopped or restarted
- **AND** captured audio continues to be retained in the current recording
- **AND** VAD continues processing new audio frames
- **AND** prior confirmed speech and voiced duration are preserved
- **AND** the consecutive-speech and trailing-silence counters restart.

#### Scenario: Automatically capture at the end of speech

- **GIVEN** the in-app camera is open
- **AND** no manual photo is pending or in flight
- **WHEN** VAD completes a valid audio turn after the configured trailing pause
- **THEN** Voice Chat captures the current camera frame automatically
- **AND** closes the camera
- **AND** normalizes and submits the frame with that completed audio turn.

#### Scenario: Capture a manual photo before the end of speech

- **GIVEN** the in-app camera is open during an active audio turn
- **WHEN** the user activates manual capture successfully
- **THEN** that frame becomes the fixed pending photo for the current turn
- **AND** the camera closes
- **AND** prior audio and confirmed speech remain intact
- **AND** the trailing-silence window restarts
- **AND** the user may continue speaking until the next valid pause.

#### Scenario: Manual capture takes precedence

- **GIVEN** manual capture is complete or in flight
- **WHEN** VAD completes the current audio turn
- **THEN** Voice Chat uses the manual capture for that turn
- **AND** does not request or retain a competing automatic frame.

#### Scenario: Submit direct audio and photo together

- **GIVEN** the selected model supports direct audio and image input together
- **AND** Voice Chat has a pending photo
- **WHEN** pause detection completes the current audio turn
- **THEN** the app submits the audio and photo as the same user turn
- **AND** persists both media inputs in the shared conversation.

#### Scenario: Submit transcribed audio and photo together

- **GIVEN** the selected model supports text and image input but not direct audio
- **AND** local transcription is available
- **AND** Voice Chat has a pending photo
- **WHEN** pause detection completes the current audio turn
- **THEN** the app transcribes the audio locally
- **AND** submits the transcript and photo as the same user turn
- **AND** persists the original audio, transcript state, and photo in the shared
  conversation.

#### Scenario: Replace a pending photo

- **GIVEN** Voice Chat already has a pending photo for the current turn
- **WHEN** the user captures another valid photo before that turn is submitted
- **THEN** the newer photo replaces the pending photo
- **AND** the replaced app-owned file is deleted when it has no other reference.

#### Scenario: Remove a pending photo

- **GIVEN** Voice Chat has a pending photo for the current turn
- **WHEN** the user removes it before turn submission
- **THEN** the audio recording continues
- **AND** the photo is excluded from the turn
- **AND** its app-owned file is deleted when it has no other reference.

#### Scenario: Camera permission is denied during listening

- **WHEN** camera permission is denied or revoked while Voice Chat is listening
- **THEN** photo capture does not start or continue
- **AND** microphone capture continues
- **AND** silence detection resumes when the permission flow closes without
  counting the suspended interval
- **AND** Voice Chat reports a controlled camera-permission state without
  stopping the voice loop.

#### Scenario: Capture is cancelled or fails during listening

- **WHEN** the user cancels photo capture or capture cannot produce a valid image
- **THEN** no pending photo is added or replaced
- **AND** microphone capture continues
- **AND** the trailing-silence window restarts without discarding prior audio
- **AND** no partial app-owned image is retained.

#### Scenario: Automatic capture fails after audio completion

- **GIVEN** VAD completed a valid audio turn while the camera was open
- **WHEN** automatic capture fails or exceeds its bounded wait
- **THEN** Voice Chat closes the camera and submits the completed audio without
  an image
- **AND** reports a recoverable camera-specific notice
- **AND** does not lose or repeat the audio turn.

#### Scenario: Do not carry camera state into the next turn

- **WHEN** a Voice Chat turn with visual context is submitted
- **THEN** its pending photo is consumed
- **AND** the camera is closed
- **AND** the next listening turn starts without camera preview or pending image
- **AND** visual context requires a new explicit camera action.

#### Scenario: Stop with a pending photo

- **GIVEN** Voice Chat has a pending photo that has not been submitted
- **WHEN** the user stops the loop or leaves Voice Chat
- **THEN** the pending photo is cleared
- **AND** its app-owned file is deleted when it has no other reference
- **AND** no generation starts from that photo.

#### Scenario: Silence does not consume the pending photo

- **GIVEN** Voice Chat has a pending photo
- **WHEN** silence or unusable speech does not produce a submitted audio turn
- **THEN** the pending photo remains available for the next valid audio turn
- **AND** it is not submitted by itself.
