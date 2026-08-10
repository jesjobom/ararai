## Why

Chat currently imports images only from the device gallery, and Voice Chat has no
way to add visual context to a spoken turn. Users need to capture what they are
looking at without leaving the conversation flow, including while Voice Chat is
actively recording their prompt.

## What Changes

- Present `Take photo` and `Choose from gallery` when the user activates the
  image action in normal Chat.
- Capture camera photos into app-owned media, then reuse the existing bounded
  image normalization, preview, persistence, and cleanup flow.
- Add a camera action to Voice Chat for image-capable models and keep microphone
  recording plus VAD active while the camera flow is open. Reset only the
  trailing-silence window when the camera opens, a manual photo completes, or
  the camera closes without a photo.
- Let the next valid trailing pause automatically capture the current camera
  frame when no manual photo is pending; a manually captured photo takes
  precedence and remains fixed until that same audio turn is submitted.
- Associate a captured Voice Chat photo with the current audio turn and submit
  both media inputs together to the selected multimodal model.
- Extend structured audio prompt content, runtime projection, persistence, and
  history rendering to retain the accompanying image.
- Handle camera permission denial, cancellation, capture/import failure, model
  capability changes, and abandoned draft photos without submitting partial
  turns or leaking app-owned files.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `local-llm-hub`: Add camera as an image source in normal Chat and allow a
  structured audio prompt to carry a captured image.
- `voice-chat`: Add uninterrupted photo capture during listening and submit the
  captured image with the current Voice Chat audio turn.

## Impact

- Affected code: Chat composer/media services, Voice Chat UI and coordinator,
  structured message models, SQLite serialization, context projection,
  LiteRT-LM content mapping, media ownership, and tests.
- Android integration: camera permission and an in-app capture surface are
  required so launching capture does not stop or restart the active microphone
  recording. Voice-turn finalization must coordinate with an in-flight automatic
  or manual photo without discarding already recorded audio.
- Runtime compatibility: the Voice Chat photo action is available only when the
  selected model/runtime can route both audio and image inputs for the same
  request; normal gallery selection remains supported.
- Validation requires automated lifecycle, persistence, capability, and cleanup
  tests plus physical-device evidence for simultaneous microphone recording and
  camera capture.
