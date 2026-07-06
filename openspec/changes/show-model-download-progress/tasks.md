# Tasks

## Proposal And Design

- [x] Create OpenSpec proposal for download progress.

## Tests First

- [x] Add failing test for downloader progress callback.
- [x] Add failing test for startup controller downloading state with progress.
- [x] Add failing test for chat ViewModel progress status text.

## Implementation

- [x] Add progress value type for downloaded bytes and optional total bytes.
- [x] Emit progress while copying model bytes to the temporary file.
- [x] Propagate progress through `ModelStartupState.Downloading`.
- [x] Format progress in `ChatViewModel` model status.
- [x] Keep retry disabled while download is in progress.

## Validation

- [x] Run `./gradlew testDebugUnitTest`.
- [x] Run `./gradlew assembleDebug`.
- [x] Run `openspec validate show-model-download-progress --strict`.
- [x] Copy the debug APK to
      `/home/node/.openclaw/jarvis/artifacts/ararai/app-debug.apk`.
