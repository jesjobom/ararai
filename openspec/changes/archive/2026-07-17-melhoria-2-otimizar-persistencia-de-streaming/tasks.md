## 1. Characterization

- [x] Characterize current completion, failure, cancellation, and navigation persistence behavior.
- [x] Define a maximum persistence interval and explicit flush triggers.

## 2. Implementation

- [x] Separate in-memory streamed rendering from durable message updates.
- [x] Add a testable batching/debounce mechanism that does not delay UI deltas.
- [x] Flush pending assistant text on completion, cancellation, failure, and lifecycle exit.
- [x] Avoid repeated full-response concatenation where a mutable buffer is appropriate.

## 3. Validation

- [x] Add tests proving multiple deltas cause fewer store updates without losing content.
- [x] Add tests for every final flush trigger and partial response recovery.
- [x] Run `./gradlew testDebugUnitTest`.
- [x] Run `./gradlew lintDebug`.
- [x] Run `./gradlew assembleDebug`.
- [x] Run `openspec validate melhoria-2-otimizar-persistencia-de-streaming --strict`.
