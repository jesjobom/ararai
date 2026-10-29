## Implementation

- [x] Reuse the detail source component for draft confirmation.
- [x] Forward the active revision's prompt from detail to the authoring form, including the resumable workflow.
- [x] Cover edit-prompt forwarding with a Compose test and retain syntax-highlighter unit coverage.
- [x] Run targeted checks and the full quality gate.
- [ ] Perform non-destructive physical UI verification; no retained-device instrumentation test execution.

## Validation evidence

- `:app:testDebugUnitTest --tests com.jesjobom.ararai.ui.JavaScriptSyntaxHighlightTest` and `:app:compileDebugAndroidTestKotlin` passed. Compose tests were compiled, not executed on the retained device.
- `scripts/quality-gate.sh` passed: Spotless, Detekt, JVM tests, Android lint, debug and release-candidate APK builds, instrumentation APK compilation, R8/artifact checks, and strict OpenSpec validation (13/13 items).
- The updated debug APK was later installed at JJ's request with `adb install -r`; package version and update time advanced while first-install time remained unchanged. See `evidence/2026-10-26-sm-s901e-debug-install.md`. In-app checks remain pending.
