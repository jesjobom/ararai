## 1. Specification and limits

- [x] Define maximum accepted source bytes, decoded dimensions, and normalized output dimensions.
- [x] Define controlled error and cleanup behavior for invalid or oversized input.

## 2. Implementation

- [x] Extract image import behind a testable app-owned media boundary.
- [x] Replace unbounded `readBytes()` import with bounded streaming or temporary-file processing.
- [x] Validate image metadata before full decode and preserve bounded bitmap allocation.
- [x] Remove partial files on failure and cancellation.
- [x] Apply EXIF rotation and mirroring before normalizing the app-owned image.

## 3. Validation

- [x] Add tests for valid images, oversized input, malformed content, provider failure, and cleanup.
- [x] Add regression tests for rotated and mirrored EXIF orientations.
- [x] Run `./gradlew testDebugUnitTest`.
- [x] Run `./gradlew lintDebug`.
- [x] Run `./gradlew assembleDebug`.
- [x] Run `openspec validate melhoria-1-limitar-processamento-de-imagens --strict`.
