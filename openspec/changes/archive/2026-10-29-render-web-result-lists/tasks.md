## Implementation

- [x] Add app-owned bounded collection-list renderer and declarative artifact.
- [x] Require explicit ranked web result count in algorithm mode and tool call validation.
- [x] Validate multi-result cardinality, fallback handling, and per-result provenance with targeted tests.
- [x] Derive the fixed ranked-web-result render artifact locally and cover retry after a missing artifact.
- [x] Render ranked links in one card and project link-only titles into compact launcher text.
- [x] Align cardinality validation with grouped rows inside one card and use a shared JS/Kotlin regression fixture.
- [x] Run full quality gate.
- [x] Install and verify the debug APK on the connected device.

## Validation evidence

- `scripts/quality-gate.sh` passed: runtime helper tests, host QuickJS, Spotless, Detekt, JVM tests (855), Android lint, debug/release-candidate builds, artifact checks, and strict OpenSpec validation (15/15). Instrumented tests were compiled, not run on the retained device.
- `adb install -r` succeeded on connected SM-S901E; installed `com.jesjobom.ararai` version changed from `202610252321` to `202610260009`.
- Physical authoring of a ten-result widget remains for the user to verify; installation/version was checked without running destructive instrumentation.
- On 2026-10-27, the retained device database showed four render-stage attempts failing with `MissingArtifact`, while algorithm and call checkpoints correctly selected `collection_list` and `limit=10`. The app now derives this fixed render artifact without model generation. An integration test reproduced a render retry after `MissingArtifact` and passed without model load or generation.
- The follow-up `scripts/quality-gate.sh` passed, including JVM tests, Detekt, lint, debug/release-candidate APKs, R8 artifact checks, and strict OpenSpec validation. `adb install -r` upgraded the connected SM-S901E to `202610270034`; `firstInstallTime` was unchanged and the retained session was still `AwaitingRetry` at `render`. The screen was locked, so physical tapping of Retry was not performed.
- The retained authoring session was retried after the device was unlocked. Its fifth render attempt succeeded in 27 ms and reached review; the user accepted and executed the widget later. A read-only database inspection found nine ranked results cached as nine sibling cards, matching the reported fragmented detail/preview layout. The runtime now returns a single card containing ordered result links, and the compact launcher projector exposes link-only titles as text. Node/JVM targeted tests and the full `scripts/quality-gate.sh` passed, including R8 and strict OpenSpec validation. Debug APK `202610270921` was installed with app data preserved. A post-install UI refresh remains unverified because the device is locked.
- On 2026-10-28 the user observed repeatable render-stage failure. The retained session showed six immediate `InvalidPresentationSuccessHandling` failures because the cardinality validator still expected the old top-level multi-card shape. The renderer now produces one card with grouped result rows, and the validator counts those rows. A fixture shared by the actual JS helper test and Kotlin pipeline test prevents the two shapes drifting again. Targeted Node/JVM tests and `scripts/quality-gate.sh` passed, including R8 and strict OpenSpec validation (15/15). Debug APK SHA-256 `80d3923025dee8a6c1a96296473346fc653f044cfcfc159810f7b5ec443d3f6c` was installed as `202610272332` with `firstInstallTime` preserved. The sanitized evidence file `evidence/2026-10-28-render-validator.md` has SHA-256 `4ae8ab597542edbc91f2aa52c15be2e5b002bee2135e2aecb10bad8e96a76aa8`. Physical retry awaits device unlock.
