# Host validation — 2026-10-18

- Base commit: `38acb8b`; implementation remains uncommitted in the working tree.
- Targeted JVM tests passed for `WidgetAuthoringServiceIntentTest`,
  `WidgetAuthoringWorkflowControllerTest`,
  `WidgetAuthoringWorkflowCoordinatorTest`, `ModelFileDownloaderTest`,
  `LiteRtLmGenerationSerializationTest`, `LiteRtLmReloadTest`,
  `LiteRtLmLocalLlmEngineTest`, `LiteRtLmWorkloadLoadingTest`,
  `BoundedCallbackDeliveryTest`, and `LiteRtLmCallbackOverflowTest`.
- `scripts/quality-gate.sh` passed on the final source tree. It ran the
  Java alignment check, Node helper tests, Firestore rules emulator tests,
  QuickJS host tests, Spotless, Detekt, all debug JVM tests, Android lint,
  debug APK/test APK assembly, release-candidate APK/test APK assembly,
  artifact checks, and `openspec validate --all --strict` (10 passed,
  0 failed).
- `git diff --check` passed.
- ADB listed only the retained Samsung SM-S901E. No destructive
  `connectedDebugAndroidTest` was run on it. No disposable arm64 target or
  physical model smoke result was available. APK assembly is not evidence of
  foreground-service or native LiteRT runtime behavior on a device.

The later, non-destructive SM-S901E manual run is recorded separately in
`2026-10-18-sm-s901e-manual-validation.md`. The disposable-device
instrumentation and Voice Chat checks remain outstanding.
