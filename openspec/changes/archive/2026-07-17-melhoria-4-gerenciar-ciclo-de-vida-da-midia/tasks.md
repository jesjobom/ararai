## 1. Ownership model

- [x] Define ownership for draft, persisted, shared, and orphaned Chat media.
- [x] Define the canonical directory and safe path-containment checks.

## 2. Implementation

- [x] Introduce a Chat media repository used by image import and audio recording.
- [x] Delete abandoned draft attachments when they are removed or replaced.
- [x] Delete unreferenced media when sessions are deleted or cleared.
- [x] Add bounded orphan reconciliation that never traverses outside Chat media storage.

## 3. Validation

- [x] Add tests for draft removal, session deletion, clear-all, shared references, and reconciliation.
- [x] Add tests proving out-of-directory and still-referenced files are preserved.
- [x] Run `./gradlew testDebugUnitTest`.
- [x] Run `./gradlew lintDebug`.
- [x] Run `./gradlew assembleDebug`.
- [x] Run `openspec validate melhoria-4-gerenciar-ciclo-de-vida-da-midia --strict`.
