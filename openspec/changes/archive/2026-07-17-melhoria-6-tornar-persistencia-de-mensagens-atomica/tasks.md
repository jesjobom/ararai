## 1. Store contract

- [x] Define append behavior for existing, missing, and concurrently deleted sessions.
- [x] Define timestamp ordering guarantees after a successful append.

## 2. Implementation

- [x] Execute message insert and session timestamp update in one SQLite transaction.
- [x] Validate the affected session-row count and fail the transaction when it is not one.
- [x] Align the in-memory store with the same missing-session behavior.

## 3. Validation

- [x] Add tests for successful atomic append and session reordering.
- [x] Add tests proving missing-session and injected update failures leave no message row.
- [x] Run `./gradlew testDebugUnitTest`.
- [x] Run `./gradlew lintDebug`.
- [x] Run `./gradlew assembleDebug`.
- [x] Run `openspec validate melhoria-6-tornar-persistencia-de-mensagens-atomica --strict`.
