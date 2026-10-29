## Implementation

- [x] Add a coordinator operation that accepts the reviewed stage and submits only an actually applied next stage.
- [x] Wire the authoring screen's Avançar action to that operation when model configuration is available; preserve acceptance-only fallback.
- [x] Add deterministic tests for successful progression and feasibility stop.
- [x] Run targeted tests and `scripts/quality-gate.sh`.
- [ ] Verify the button flow on a physical device without destructive instrumentation execution.

## Validation evidence

- `:app:testDebugUnitTest --tests com.jesjobom.ararai.widget.managed.WidgetAuthoringWorkflowCoordinatorTest` passed, including one-tap progression and feasibility stop. `:app:compileDebugAndroidTestKotlin` passed; instrumentation tests were compiled, not executed on the retained device.
- `scripts/quality-gate.sh` passed: Spotless, Detekt, JVM tests, Android lint, debug and release-candidate APKs, R8/artifact checks, and strict OpenSpec validation (14/14 items).
- The debug APK containing this change has not yet been installed or physically verified.
