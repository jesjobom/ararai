# Validation

- `WidgetAuthoringWorkflowProjectionTest` passed for `unachievable` (legacy schema 1) and `needs_clarification` (schema 3); both expose the message and omit Continue from the UI projection.
- `scripts/quality-gate.sh` passed, including Android unit tests, Detekt, lint, releaseCandidate assembly, APK verification, and strict OpenSpec validation (11 items, 0 failures).
- ReleaseCandidate APK: version `202611072345`, versionCode `29901885`, SHA-256 `ba4b019cea4c559c0d8c4786f597f829613b2c064b15b50d3a8ff873e24828c0`.
- `adb install -r` on connected SM-S901E returned `Success`; installed package reports the new version and unchanged `firstInstallTime` (`2026-10-04 22:04:26`). The app launched normally and was returned to the launcher without altering the active widget authoring session.
- The active device session was at Presentation/device recovery, so a terminal-feasibility screen was not available for visual confirmation. The UI behavior is covered by the projection test and compiled releaseCandidate build, but has not been manually observed on this device.
