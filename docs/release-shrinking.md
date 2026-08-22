# Release shrinking evidence

## Build boundary

The production `release` build and the reproducible `releaseCandidate` build use
the same optimized R8 and resource-shrinking configuration. Production release
uses the external upload key. Release candidate uses Android's local debug key
only so CI and maintainers can assemble and install equivalent shrunk code
without access to production signing material. It is not a publishable artifact.

Both variants run on the canonical JDK 17 Android Gradle runtime. The required
automated gate assembles `releaseCandidate` and fails unless R8 emits non-empty
`mapping.txt`, `seeds.txt`, `usage.txt`, and `configuration.txt` files.
`scripts/build-release-bundle.sh` verifies the production signature and copies
the production mapping artifacts beside the external AAB handoff. Generated
APKs, bundles, mappings, signing environments, password files, and keystores are
excluded from Git.

## Boundary inventory and keep-rule decisions

- **Whisper JNI:** `libararai_whisper.so` exports symbols containing the exact
  `com.jesjobom.ararai.whisper.WhisperRuntime` class and method names. That
  object is kept explicitly.
- **LiteRT-LM JNI:** version 0.14.0 contains name-based native methods and
  native-to-Java callbacks but does not ship complete consumer ProGuard rules.
  Physical release-candidate testing proved that native code also looks up
  methods on Kotlin DTOs such as `SamplerConfig` and `BenchmarkInfo`. The
  LiteRT-LM Java/Kotlin package is therefore preserved as one JNI boundary;
  application code and unrelated dependencies remain shrinkable.
- **Firebase:** Authentication, App Check, Firestore, Tasks, and their
  transitive dependencies provide their own maintained consumer rules. No
  application-wide Firebase keep rule is added.
- **Compose and AndroidX:** compiler-generated reachability and dependency
  consumer rules cover the statically referenced UI. ArarAI does not resolve
  destinations or composables by class name.
- **Gson and persistence:** application JSON parsing is explicit for runtime
  provider data. Comparison-export DTOs are directly reachable from their
  serializers. SQLite rows are mapped manually. No blanket model keep rule is
  justified.
- **EvalEx:** its bytecode references the compile-only `lombok.Generated`
  marker, and runtime operator/function discovery inspects annotations. A
  narrow warning suppression plus keep rules for operator and function classes
  preserve those reflective boundaries.
- **Reflection:** ArarAI's LiteRT-LM tools implement `OpenApiTool` directly;
  they do not use the SDK's optional Kotlin-reflection tool adapter.

## Size evidence

The signed, unshrunk production AAB built immediately before enabling R8 was
50,973,119 bytes (SHA-256
`7aeaf7bca75cee9dbf987e536ed2613ad86fd793c4881925deaaf1c8dfac9eda`).
The signed, shrunk production AAB is 40,353,499 bytes (SHA-256
`7468f28ce43bf3c4a79b434a941122bf663715379cb9dc0c65e20a3a76ca9815`):
10,619,620 bytes, or 20.83%, smaller than the baseline. The locally signed
release-candidate APK is 52,174,919 bytes. Size is supporting evidence only; it
does not prove runtime compatibility.

The current locally signed release-candidate APK is 53,310,651 bytes (SHA-256
`fd24168ec49a4384147c1894cfdf912867ae33a6bb918331a0ee36580bcabcdc`).

The production build produced all four diagnostic artifacts. Their verified
handoff location is `artifacts/ararai/release-diagnostics-vc2/`, alongside the
fixed-name production AAB. These files are intentionally external to Git.

## Required physical smoke matrix

Install the signed `releaseCandidate` APK on an arm64 device and record device,
Android version, app version, model artifacts, and result in
`docs/device-validation.md`. Acceptance requires all of the following:

1. cold startup and navigation through Home, Chat, Voice Chat, Models,
   Assistant configuration, and Settings;
2. existing SQLite conversation load plus a new text/media message round trip;
3. Gemma LiteRT-LM model load and one completed generation;
4. Whisper model load, `systemInfo`, and one JNI transcription;
5. camera/image and audio attachment flows relevant to the installed models;
6. generated-content reporting through Firebase Auth, Play Integrity App Check,
   and Firestore using an official distribution path where App Check permits it;
7. process restart followed by another navigation and persistence check.

Automated assembly, mapping availability, or size reduction must not mark this
matrix as passed.
