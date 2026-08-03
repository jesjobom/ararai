## MODIFIED Requirements

### Requirement: Structured Multimodal Messages

The chat domain SHALL represent user messages as structured prompt content where
images are attachments to text prompts and audio is an alternative prompt
modality. An image-only first turn SHALL use the default image-description prompt
as its automatic session title.

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
- **WHEN** the user submits one or more image attachments without typed text
- **THEN** the send action is enabled
- **AND** the chat request includes a default image-description text prompt
- **AND** the app sends the image attachments to the local inference engine.

#### Scenario: Audio prompt

- **GIVEN** the selected model supports audio input
- **WHEN** the user submits an audio prompt
- **THEN** the chat request contains audio prompt content
- **AND** the app sends the audio directly to the local inference engine
- **AND** the app does not transcribe the audio before generation.

#### Scenario: Audio prompt cannot include text

- **GIVEN** the user has selected or recorded an audio prompt
- **WHEN** the chat draft is in audio prompt mode
- **THEN** the app does not allow accompanying text to be submitted in the same
  request.

#### Scenario: Empty draft cannot be submitted

- **GIVEN** the chat draft has no text prompt, no image attachment, and no
  audio prompt
- **WHEN** the user opens the chat composer
- **THEN** the send action remains disabled.

#### Scenario: Image-only prompt titles a new session

- **GIVEN** a new Chat session and a model that supports image input
- **WHEN** the user submits one or more image attachments without typed text
- **THEN** the request includes the default image-description text prompt
- **AND** the session title uses that prompt instead of remaining `New chat`.

## ADDED Requirements

### Requirement: Bounded Multimodal Follow-up Context

Chat SHALL keep the most recent historical image set available to a subsequent
textual follow-up while avoiding unbounded historical media replay.

#### Scenario: Textual follow-up refers to the recent image

- **GIVEN** Chat history contains a user turn with image attachments
- **WHEN** the user submits a later text prompt without a new image
- **THEN** the request includes the attachments from the most recent image turn
- **AND** retains bounded textual conversation history.

#### Scenario: Current images replace historical image context

- **GIVEN** Chat history contains image attachments
- **WHEN** the user submits a prompt with new image attachments
- **THEN** the request includes only the newly submitted images
- **AND** does not mix them with historical images.
