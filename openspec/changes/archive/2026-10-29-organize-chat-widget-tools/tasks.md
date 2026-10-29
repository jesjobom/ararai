## Implementation

- [x] Introduce independent persisted consumer enablement with a one-time migration preserving current effective Chat and widget states; keep provider configuration shared.
- [x] Expose consumer-specific tool status, eligibility and readiness without exposing credentials; retain widget revision allowlist checks.
- [x] Keep the widget list as the Widgets landing screen and add a top-right tools action, preserving creation, detail, and launcher deep links.
- [x] Scope Assistant Tools labels to Chat and provide a provider-configuration action where a credentialed provider is available.
- [x] Implement the current providers' token dialog with masked entry, verification, save, removal, failure handling and refreshed status; keep the provider-specific configuration entry point extensible.
- [x] Route widget-authoring blocked-tool actions to widget tools and back to the in-progress authoring flow.
- [x] Add targeted migration and Compose navigation/dialog coverage; run targeted tests and `scripts/quality-gate.sh`.
- [ ] Perform non-destructive device navigation/dialog verification when available. Instrumentation APKs were built but not executed on JJ's retained device.

## Validation evidence

- `:app:testDebugUnitTest --tests com.jesjobom.ararai.widget.WidgetToolPreferencesTest` passed.
- `:app:compileDebugAndroidTestKotlin` passed; Compose tests were compiled, not executed.
- `scripts/quality-gate.sh` passed, including unit tests, Spotless, Detekt, lint, APK builds, R8, artifact checks, and strict OpenSpec validation.
- The debug APK was installed with `adb install -r` on SM-S901E and package version/update time were verified; see `evidence/2026-10-25-sms901e-debug-install.md`. Visual and interaction checks remain pending.
