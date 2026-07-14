# Design: Multimodal Message Support

## Current State

- `PromptRequest` contains only a `String`.
- `ChatMessage` and `StoredChatMessage` contain only text.
- SQLite stores chat messages in a `text TEXT NOT NULL` column.
- The Compose chat input exposes a text field and send button only.
- `LiteRtLmLocalLlmEngine` calls `conversation.sendMessageAsync(prompt, ...)`,
  which uses the text-only path even though LiteRT-LM exposes content parts.
- `LlamaCppLocalLlmEngine` and its JNI bridge accept only text prompts.

## Domain Shape

Introduce structured prompt content where image is an attachment to text, while
audio is its own prompt modality:

```kotlin
data class ChatMessage(
    val role: ChatRole,
    val content: MessageContent,
    val id: String = "",
)

sealed interface MessageContent {
    data class TextPrompt(
        val text: String,
        val imageAttachments: List<ImageAttachment> = emptyList(),
    ) : MessageContent

    data class AudioPrompt(
        val uri: String,
        val mimeType: String,
        val displayName: String?,
        val byteSize: Long?,
        val durationMillis: Long?,
    ) : MessageContent
}

data class ImageAttachment(
    val uri: String,
    val mimeType: String,
    val displayName: String?,
    val byteSize: Long?,
)
```

The exact Kotlin names can change during implementation, but the important
boundary is that audio is not treated as an attachment to text. A user request
is either a text prompt, optionally with images, or an audio prompt.

Assistant responses can remain text prompts initially, because the selected
models generate text responses in this slice.

Rejected shape:

```kotlin
data class AudioPlusText(
    val text: String,
    val audio: AudioPrompt,
)
```

The app should not create this shape because text and audio are mutually
exclusive prompt inputs.

Alternative lower-level runtime content may still be represented as ordered
parts inside an engine adapter if the provider API requires it, but that is an
adapter detail, not the chat domain model.

For persistence, image attachments and audio prompt media share the same local
file-storage concern, but they keep different domain roles.

```kotlin
sealed interface StoredMediaReference {
    data class ImageAttachment(
        val uri: String,
        val mimeType: String,
        val displayName: String?,
        val byteSize: Long?,
    ) : StoredMediaReference

    data class AudioPrompt(
        val uri: String,
        val mimeType: String,
        val displayName: String?,
        val byteSize: Long?,
        val durationMillis: Long?,
    ) : StoredMediaReference
}
```

## Model Capabilities

Add model configuration fields for input modalities, for example:

```properties
models.2.capabilities.input.text=true
models.2.capabilities.input.image=true
models.2.capabilities.input.audio=true
```

Text defaults to supported for existing entries. Image and audio default to
unsupported unless explicitly declared. The runtime layer must still validate
that it can honor the declared modality, so catalog metadata is necessary but
not sufficient.

This keeps product behavior deterministic: the UI does not infer support from a
model name or artifact extension.

## Runtime Behavior

LiteRT-LM is the first runtime target for multimodal messages. It should convert
message parts into LiteRT-LM content parts and configure vision or audio backend
support when the selected model declares those capabilities.

llama.cpp remains text-only in this change. Its upstream multimodal support
requires model-projector metadata and JNI/native API work that should be scoped
as a separate change.

## Audio Policy

Audio is sent as model input when supported by the selected model/runtime. The
app does not transcribe audio before sending it in this change. Audio is a prompt
modality, not an attachment, so the composer must not allow text and audio in
the same user request. Text transcription can be added later as a fallback for
text-only models, but that is not part of this restructuring.

## Storage

Persist messages as structured content. A pragmatic first version can store the
content as JSON in SQLite and keep media files in app-owned storage. Existing
rows with only text should migrate to one text-prompt content value with no
image attachments.

The app should avoid depending on external content URIs staying readable after
the user leaves the picker. Imported image/audio files should be copied into
app-owned attachment storage before being referenced by chat history.

## UI

The composer exposes:

- text input for text-capable models
- image picker when image input is supported and the current prompt mode is text
- audio file picker or recorder when audio input is supported and the current
  prompt mode is audio

The send action is enabled when the draft contains either non-empty text or one
audio prompt. Image-only submission is valid for image-capable models; the chat
adapter supplies a small default task prompt so the model receives both a visual
input and a clear instruction. If the selected model changes to a text-only
model, unsupported draft content is removed or the draft is reset with a
visible, controlled state update.

Large camera images should not be forwarded directly to LiteRT-LM. On import,
the app copies the media into app-owned storage, normalizes image attachments to
JPEG, and constrains the longest side to a mobile-friendly size before the
runtime sees the file. The same app-owned file can be decoded at thumbnail size
for draft and chat-history previews.
