## Implementation

- [x] Diagnose the retained render attempt and distinguish generation timeout from recovery timeout.
- [x] Release the shared engine before recovery sampling and cover stale-session behavior with a deterministic unit test.
- [x] Run the full quality gate.
- [x] Install the debug APK with retained device data and verify the package version.
- [x] Retry the retained render stage and record sanitized device evidence.
