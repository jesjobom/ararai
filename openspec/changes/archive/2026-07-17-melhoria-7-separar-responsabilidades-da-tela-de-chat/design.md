## Context

`ChatScreen.kt` had grown to 1,402 lines and combined screen orchestration,
session dialogs, composer state, Android permission launchers, image import,
`AudioRecord`, WAV encoding, `MediaPlayer`, bitmap decoding, and direct file
deletion. Improvements 1 and 4 established safe import/media ownership, while
improvement 8 established JVM, lint, build, instrumentation, and device gates.

## Goals / Non-Goals

- Make Android media implementations replaceable through explicit interfaces.
- Keep Compose presentation free from direct bitmap, player, recorder, and file I/O.
- Split presentation by screen, session, input, and message/media responsibility.
- Preserve ViewModel/store ownership, navigation, inference flow, and persistence encoding.
- Add no new user-facing behavior or database migration.

## Decisions

### Media service bundle at the Activity boundary

`MainActivity` creates `ChatMediaServices` once from the Android context and
`ChatMediaRepository`, then passes it through `ArarAiApp` into Chat presentation.
The bundle exposes focused interfaces for image import/decoding, recording,
playback, and draft cleanup. Tests can replace every implementation without
constructing `AudioRecord`, `MediaPlayer`, `BitmapFactory`, or a real provider.

### Presentation split

- `ChatScreen.kt`: route/screen orchestration and durable ViewModel actions.
- `ChatSessionComponents.kt`: session/settings/rename dialogs and controls.
- `ChatInputComponents.kt`: composer and transient recording presentation state.
- `ChatMessageMediaComponents.kt`: message, attachment, thumbnail, and playback UI.
- `ChatImageImporter.kt`: bounded image I/O.
- `ChatAudioRecording.kt`: audio capture and WAV encoding.
- `ChatMediaServices.kt`: injectable contracts and Android player/decoder adapters.

The screen file is reduced from 1,402 to 363 lines. Navigation remains in
`ArarAiApp`; Chat sessions/inference remain in `ChatViewModel`; durable content
and encoding remain in `ChatSessionStore`.

### Lifecycle ownership

Compose owns only transient UI state and calls lifecycle methods on interfaces.
Disposal stops/discards recording and releases playback. Android adapters own
native objects and release partially initialized `MediaPlayer` instances on error.
Draft deletion remains constrained by the media repository from improvement 4.

## Compatibility

`MessageContent`, `ImageAttachment`, `AudioPrompt`, SQLite payload encoding,
permission strings, attachment limits, WAV format, cancellation actions, and UI
labels remain unchanged. Existing characterization and Android-boundary tests
therefore guard the refactor rather than defining a new product contract.
