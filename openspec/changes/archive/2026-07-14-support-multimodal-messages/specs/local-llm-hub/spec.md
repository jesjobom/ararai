## ADDED Requirements

### Requirement: Model Input Capability Metadata

The configured model catalog SHALL declare the input modalities supported by each
model/runtime combination.

#### Scenario: Default existing models to text input

- **GIVEN** a configured model entry does not declare explicit input
  capabilities
- **WHEN** the model catalog is parsed
- **THEN** the app treats text input as supported
- **AND** treats image and audio input as unsupported.

#### Scenario: Parse explicit multimodal capabilities

- **GIVEN** a configured model entry declares text, image, or audio input
  capabilities
- **WHEN** the model catalog is parsed
- **THEN** the resulting model metadata exposes those capabilities to chat UI
  state and runtime validation.

#### Scenario: Do not infer media support from model name

- **GIVEN** a configured model name or artifact URL contains words that suggest
  vision, image, audio, or multimodal support
- **WHEN** the model entry lacks explicit media input capabilities
- **THEN** the app does not show image or audio input controls for that model.

### Requirement: Structured Multimodal Messages

The chat domain SHALL represent user messages as structured prompt content where
images are attachments to text prompts and audio is an alternative prompt
modality.

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

### Requirement: Image Input Normalization

The app SHALL copy selected image inputs into app-owned storage and normalize
large camera images before sending them to multimodal runtimes.

#### Scenario: Normalize selected image

- **GIVEN** the user selects an image attachment
- **WHEN** the app imports the image
- **THEN** it decodes the selected image
- **AND** writes an app-owned JPEG copy
- **AND** constrains the longest side to a fixed mobile-friendly input size
- **AND** sends the normalized file path to the local inference engine.

#### Scenario: Render image thumbnail in chat

- **GIVEN** a draft or stored chat message contains an image attachment
- **WHEN** the chat screen renders it
- **THEN** the user can see a thumbnail preview of the local image file.

### Requirement: Capability-Gated Chat Controls

The chat UI SHALL expose media input controls only when the selected model and
runtime support the corresponding modality.

#### Scenario: Hide image action for image-unsupported model

- **GIVEN** the selected model does not support image input
- **WHEN** the chat composer is displayed
- **THEN** no image attachment action is presented.

#### Scenario: Hide audio action for audio-unsupported model

- **GIVEN** the selected model does not support audio input
- **WHEN** the chat composer is displayed
- **THEN** no audio attachment action is presented.

#### Scenario: Show supported media actions

- **GIVEN** the selected model supports image and audio input
- **AND** the selected runtime implementation supports both modalities
- **WHEN** the chat composer is displayed
- **THEN** the image and audio actions are presented.

### Requirement: Multimodal Runtime Boundary

The local inference engine boundary SHALL accept structured message parts and
validate modality support before generation.

#### Scenario: Reject unsupported multimodal request before inference

- **GIVEN** the selected runtime is text-only
- **AND** a chat request contains image attachments or an audio prompt
- **WHEN** generation is requested
- **THEN** the app returns a controlled generation failure
- **AND** it does not call the native text-only inference path.

#### Scenario: Send multimodal content through LiteRT-LM

- **GIVEN** the selected model uses the LiteRT-LM runtime
- **AND** the selected model declares support for every input modality in the
  request
- **WHEN** generation is requested with a text prompt, image attachments, or an
  audio prompt
- **THEN** the LiteRT-LM engine converts that structured request to LiteRT-LM
  content
- **AND** sends them to the LiteRT-LM conversation API.

#### Scenario: Keep llama.cpp text-only for this change

- **GIVEN** the selected model uses the llama.cpp runtime
- **WHEN** chat generation is requested
- **THEN** text-only requests continue to use the existing llama.cpp path
- **AND** requests containing image attachments or audio prompts are rejected
  before JNI.

### Requirement: Multimodal Chat Persistence

The app SHALL persist structured chat content locally so multimodal chat
history can be displayed after app restart.

#### Scenario: Persist structured chat content

- **GIVEN** a submitted user message contains a text prompt with image
  attachments or an audio prompt
- **WHEN** the message is stored
- **THEN** the prompt content is persisted
- **AND** image and audio references point to app-owned local media files.

#### Scenario: Migrate existing text-only history

- **GIVEN** existing persisted chat messages contain only text
- **WHEN** the app opens the upgraded chat store
- **THEN** each existing message is represented as structured text-prompt
  content with no image attachments
- **AND** the existing conversation remains visible.

#### Scenario: Render stored multimodal messages

- **GIVEN** chat history contains stored image attachments or audio prompts
- **WHEN** the chat screen renders the conversation
- **THEN** the user can see image attachments with their text prompt
- **AND** audio prompts render as standalone prompt messages.
