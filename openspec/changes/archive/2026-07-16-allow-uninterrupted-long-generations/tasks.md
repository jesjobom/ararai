## 1. Specification

- [x] Define user-controlled scrolling during streamed responses.
- [x] Define removal of implicit chunk-based generation cancellation.

## 2. Implementation

- [x] Follow a bottom anchor while automatic following is enabled.
- [x] Detach automatic following on user drag and restore it at the bottom.
- [x] Remove LiteRT-LM callback-chunk cancellation.
- [x] Preserve explicit generation cancellation.

## 3. Validation

- [x] Add regression coverage for LiteRT-LM streams longer than `maxTokens`
  callback chunks.
- [x] Run OpenSpec strict validation.
- [x] Run Android unit tests and debug APK assembly.
