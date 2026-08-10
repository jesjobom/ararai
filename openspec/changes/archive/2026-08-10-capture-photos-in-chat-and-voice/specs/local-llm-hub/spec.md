## ADDED Requirements

### Requirement: Chat Camera Capture

Normal Chat SHALL let users choose between taking a new photo and selecting an
existing gallery image whenever image input is available. New photos SHALL use
the same app-owned normalization, preview, persistence, and cleanup guarantees
as gallery imports.

#### Scenario: Choose an image source

- **GIVEN** the selected model and runtime support image input
- **WHEN** the user activates the image action in normal Chat
- **THEN** the app presents actions to take a photo or choose an image from the
  gallery
- **AND** choosing the gallery preserves the existing image-selection flow.

#### Scenario: Capture a photo for the Chat draft

- **GIVEN** the image-source actions are displayed
- **WHEN** the user chooses to take a photo and completes capture
- **THEN** the app imports and normalizes the captured image into app-owned
  storage
- **AND** shows it in the Chat draft using the same attachment behavior as a
  gallery image.

#### Scenario: Camera permission is denied

- **WHEN** camera permission is denied or revoked
- **THEN** the app does not start or continue camera capture
- **AND** reports a controlled permission state
- **AND** leaves the existing Chat draft unchanged
- **AND** still permits gallery selection when image input remains available.

#### Scenario: Camera capture is cancelled or fails

- **WHEN** the user cancels camera capture or capture cannot produce a valid
  image
- **THEN** no image is added to the Chat draft
- **AND** no partial app-owned image is retained
- **AND** existing draft content remains unchanged.

### Requirement: Multimodal Audio Turn Media Ownership

An image attached to an audio prompt SHALL follow the same app-owned persistence,
reference tracking, rendering, and deletion rules as an image attached to a text
prompt.

#### Scenario: Persist audio with an image

- **GIVEN** a submitted user message contains an audio prompt and an image
  attachment
- **WHEN** the message is stored
- **THEN** both media references point to app-owned local files
- **AND** reopening the conversation renders the audio prompt and image as one
  user turn.

#### Scenario: Delete audio with an image

- **GIVEN** an audio prompt and its image are no longer referenced by any stored
  message or active draft
- **WHEN** media cleanup runs
- **THEN** both app-owned media files are eligible for deletion
- **AND** files still referenced elsewhere are preserved.

### Requirement: Expand Historical Chat Images

Normal Chat SHALL let the user open an enlarged view of any image rendered in
conversation history, including images attached to text and audio prompts.

#### Scenario: Open and close a historical image

- **GIVEN** a stored Chat message renders an image attachment
- **WHEN** the user activates the image thumbnail
- **THEN** the app displays a larger fitted view of that image
- **AND** the user can close the view explicitly or with the system back action
- **AND** closing the view returns to the same conversation position.

## MODIFIED Requirements

### Requirement: Structured Multimodal Messages

The chat domain SHALL represent user messages as structured prompt content where
images may accompany either a text prompt or the current audio prompt. An
image-only first turn SHALL use the default image-description prompt as its
automatic session title.

#### Scenario: Text-only message remains supported

- **GIVEN** the selected model supports text input
- **WHEN** the user submits a non-empty text prompt with no media
- **THEN** the chat request contains a text prompt with no image attachments
- **AND** generation proceeds through the selected local runtime.

#### Scenario: Text prompt with image attachments

- **GIVEN** the selected model supports image input
- **AND** the selected model supports text input
- **WHEN** the user submits a text prompt with one or more image attachments
- **THEN** the chat request contains the text prompt and image attachments
- **AND** the app sends the structured request to the local inference engine.

#### Scenario: Image-only prompt

- **GIVEN** the selected model supports image input
- **WHEN** the user submits one or more image attachments without typed text or
  audio
- **THEN** the send action is enabled
- **AND** the chat request includes a default image-description text prompt
- **AND** the app sends the image attachments to the local inference engine.

#### Scenario: Audio prompt

- **GIVEN** the selected model supports audio input
- **WHEN** the user submits an audio prompt without an image
- **THEN** the chat request contains audio prompt content
- **AND** the app sends the audio directly to the local inference engine
- **AND** the app does not transcribe the audio before generation.

#### Scenario: Audio prompt with image attachment

- **GIVEN** the selected model and runtime support audio and image input in one
  request
- **WHEN** the user submits an audio prompt with a current image attachment
- **THEN** the chat request contains the audio and image as parts of the same
  user turn
- **AND** the app sends both media inputs to the local inference engine.

#### Scenario: Transcribed audio prompt with image attachment

- **GIVEN** the selected model supports text and image input but not direct audio
- **AND** local transcription is available
- **WHEN** the user submits an audio prompt with a current image attachment
- **THEN** the app transcribes the audio locally
- **AND** submits the resulting text and image as parts of the same user turn
- **AND** persists the original audio, transcript state, and image together.

#### Scenario: Audio prompt cannot include text

- **GIVEN** the user has selected or recorded an audio prompt
- **WHEN** the chat draft is in audio prompt mode
- **THEN** the app does not allow accompanying typed text to be submitted in the
  same request
- **AND** this restriction does not prevent an image from accompanying the
  audio prompt.

#### Scenario: Empty draft cannot be submitted

- **GIVEN** the chat draft has no text prompt, no image attachment, and no audio
  prompt
- **WHEN** the user opens the chat composer
- **THEN** the send action remains disabled.

#### Scenario: Image-only prompt titles a new session

- **GIVEN** a new Chat session and a model that supports image input
- **WHEN** the user submits one or more image attachments without typed text or
  audio
- **THEN** the request includes the default image-description text prompt
- **AND** the session title uses that prompt instead of remaining `New chat`.
