## 1. Specification

- [x] Define cancellation, back navigation, build-version, and catalog cleanup
  requirements.

## 2. Implementation

- [x] Make Android back return internal screens to Home.
- [x] Add cancel download support in the model catalog controller and UI.
- [x] Add cancel generation support in chat state, view model, and UI.
- [x] Add cancel benchmark support in benchmark state, view model, and UI.
- [x] Generate app version label from build timestamp.
- [x] Remove Gemma GGUF CPU and Phi-4 from checked-in model catalog.
- [x] Preserve streaming download-to-disk behavior and cleanup temp files on
  cancellation.

## 3. Validation

- [x] Add/update focused JVM tests.
- [x] Run JVM tests.
- [x] Build debug APK and copy handoff artifact.
