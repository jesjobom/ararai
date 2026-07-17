## 1. Quality-gate definition

- [x] Inventory JVM, Android, JNI/native, and physical-device validation coverage.
- [x] Define required CI checks and the device-only validation matrix.

## 2. Automation

- [x] Add CI for `testDebugUnitTest`, `lintDebug`, strict OpenSpec validation, and `assembleDebug`.
- [x] Add instrumentation scaffolding and focused tests for permissions, content providers, lifecycle, and backup configuration.
- [x] Add a bounded JNI/native smoke test where the runtime contract allows it.
- [x] Publish or retain useful failure artifacts without publishing private app data.

## 3. Device validation

- [x] Version a physical-device checklist for llama.cpp and LiteRT-LM paths.
- [x] Cover GPU selection, cancellation, repeated runs, lifecycle, memory pressure, and thermal observations.
- [x] Run the complete gate and document any environment-only exclusions.
- [x] Run `openspec validate melhoria-8-criar-barreira-de-qualidade-android-nativa --strict`.
