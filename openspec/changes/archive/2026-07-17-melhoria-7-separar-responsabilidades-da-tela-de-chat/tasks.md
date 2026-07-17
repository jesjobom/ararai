## 1. Characterization and boundaries

- [x] Complete or coordinate improvements 1, 4, and 8 before structural changes.
- [x] Characterize attachment, recording, playback, permission, cancellation, and lifecycle behavior.
- [x] Define component and service ownership without changing persisted formats.

## 2. Implementation

- [x] Extract image/media I/O from Compose code behind injectable interfaces.
- [x] Extract audio recording and playback lifecycle from Chat presentation.
- [x] Split the Chat screen into cohesive presentation components.
- [x] Keep navigation, durable Chat state, and inference coordination at their existing architectural boundaries.

## 3. Validation

- [x] Run focused characterization tests after each extraction step.
- [x] Add instrumentation coverage for permission and lifecycle-sensitive behavior.
- [x] Run `./gradlew testDebugUnitTest`.
- [ ] Run `./gradlew connectedDebugAndroidTest` on an available device (no ADB device connected in this environment).
- [x] Run `./gradlew lintDebug`.
- [x] Run `./gradlew assembleDebug`.
- [x] Run `openspec validate melhoria-7-separar-responsabilidades-da-tela-de-chat --strict`.
