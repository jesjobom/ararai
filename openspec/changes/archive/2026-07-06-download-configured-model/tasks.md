# Tasks

## Download Core

- [x] Add failing tests for successful download to temporary file and atomic
      promotion to final path.
- [x] Add failing tests for byte-size mismatch and SHA-256 mismatch.
- [x] Add failing tests for missing file, invalid file, failed download, and
      retry state.
- [x] Implement a downloader boundary that streams the configured model source
      to a sibling temporary file.
- [x] Validate byte size and SHA-256 before promotion.
- [x] Promote the validated temporary file to the configured final path with a
      same-filesystem atomic rename.
- [x] Clean up stale temporary files after success or validation failure.

## App Integration

- [x] Trigger download automatically when startup resolution reports missing or
      invalid model.
- [x] Update model state after download succeeds.
- [x] Keep chat submission disabled until the model is available.
- [x] Surface missing, downloading, available, and failed states in the debug
      UI.
- [x] Add a retry action for failed downloads.

## Validation

- [x] Run targeted unit tests.
- [x] Run `./gradlew testDebugUnitTest`.
- [x] Run `./gradlew assembleDebug`.
- [x] Run `openspec validate download-configured-model --strict`.
- [x] Copy the debug APK to
      `/home/node/.openclaw/jarvis/artifacts/ararai/app-debug.apk`.
