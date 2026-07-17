## 1. Lifecycle contract

- [x] Document ownership transitions among active, retained, cancelled, and closed conversations.
- [x] Define idempotent cancel and close expectations for the bridge boundary.

## 2. Implementation

- [x] Centralize retained-conversation invalidation and resource closure.
- [x] Ensure cancellation closes the discarded conversation exactly once.
- [x] Apply the same invalidation rule to error, profile/model replacement, and unload paths.

## 3. Validation

- [x] Add tests for cancel during generation, cancel after retention, cancel then generate, and cancel then unload.
- [x] Verify compatible successful generations still reuse one conversation.
- [x] Run `./gradlew testDebugUnitTest`.
- [x] Run `./gradlew lintDebug`.
- [x] Run `./gradlew assembleDebug`.
- [x] Run `openspec validate melhoria-3-corrigir-cancelamento-litert --strict`.
