# ArarAI quality gates

## Coverage inventory

- JVM/Robolectric (`app/src/test`): model catalog/download/integrity, Chat state
  and persistence, prompt construction, engine orchestration, streaming durability,
  bounded history paging, persistence dispatch, media ownership/import limits,
  Voice Chat media-copy failure, controller lifecycle cancellation, LiteRT
  ownership/reuse policy, and backup policy. Managed-widget coverage includes
  revisioned SQLite state, scheduling policy, execution leases, proposal/consent
  boundaries, cached renderer decoding, canonical Wikipedia URI allowlisting,
  deterministic Portuguese/English fixtures, and a complete provider-fake
  lifecycle with no live network dependency.
- Android instrumentation (`app/src/androidTest`): runtime permission/backup
  manifest configuration, real `ContentResolver` image import, MainActivity
  stop/resume, platform text-to-speech boundaries, and the two-phase widget
  coordinator over real QuickJS isolates. `quickjs-runtime/src/androidTest`
  holds the complete Android sandbox/adversarial corpus. Managed-widget Compose
  tests cover fixed presentation nodes, inert unsafe links, empty/list/detail
  state, and guarded management actions.
- Host-native QuickJS (`quickjs-runtime/src/hostTest`): compiles the exact
  vendored QuickJS sources and the same project-owned sandbox core used by JNI,
  then executes real JavaScript control-flow, isolation, forbidden-surface,
  malformed-input, output, memory, stack, deadline, cancellation, recovery, and
  repeated-lifecycle tests in sanitized debug and optimized release builds.
  AddressSanitizer covers the full debug executable; UndefinedBehaviorSanitizer
  covers the project-owned C++ boundary. Vendored QuickJS C sources are excluded
  only from UBSan because their intentional signed bit operations trigger
  upstream diagnostics.
- Native Android build: `assembleDebug` compiles and packages the pinned
  whisper.cpp and QuickJS JNI libraries for arm64-v8a.
- Physical device (`docs/device-validation.md`): real model inference, backend
  selection/fallback, cancellation, repeated runs, lifecycle, memory, thermal,
  permissions, storage cleanup, backup, and device transfer. The optimized
  release-candidate APK also contains a separate self-service QuickJS/runtime
  validator that exports bounded physical evidence without ADB.

## Required automated gate

`scripts/quality-gate.sh` is the local and CI entry point. It runs:

1. Java runtime declaration and active-runtime checks
2. Firestore Security Rules tests with the isolated JDK 21 Firebase runtime
3. host-native QuickJS debug execution with AddressSanitizer and
   UndefinedBehaviorSanitizer, followed by optimized release execution
4. `spotlessCheck` with Spotless 7.2.1 and ktlint 1.7.1
5. Detekt 2.0.0-alpha.5 with the reviewed baseline in `config/detekt/baseline.xml`
6. `testDebugUnitTest`
7. `lintDebug`
8. `assembleDebug`
9. `assembleDebugAndroidTest`
10. optimized `releaseCandidate` app and release QuickJS instrumentation APK
    assembly
11. shrunk `assembleReleaseCandidate`, R8 diagnostic-artifact verification, and
    release-candidate manual-validator/arm64 artifact verification
12. `openspec validate --all --strict`

Any failure makes the gate fail. GitHub Actions runs the same script for pull
requests, pushes to `main`, and manual dispatches. The canonical Android Gradle runtime is Temurin JDK 17.
The workflow installs Temurin JDK 21 first, preserves it as
`FIREBASE_JAVA_HOME`, then installs JDK 17 last so every Gradle invocation uses
the declared Android baseline. `scripts/run-firestore-rules-tests.sh` temporarily
selects only the preserved JDK 21 for the Firebase Emulator process. Local runs
must provide the same `JAVA_HOME`/`FIREBASE_JAVA_HOME` split.

Toolchain inputs are pinned to JDK 17 for Gradle, JDK 21 for Firebase, Android
36, Build Tools 36.0.0, NDK 28.2.13676358, CMake 3.22.1, Gradle
9.4.1 through the wrapper, and OpenSpec 1.6.0. The host-native suite uses the
CI runner's explicitly selected `cc`/`c++` and CMake toolchain; local overrides
are available through `ARARAI_HOST_CC`, `ARARAI_HOST_CXX`,
`ARARAI_HOST_CMAKE`, and `ARARAI_HOST_CTEST`.

`scripts/check-java-runtime-alignment.sh` fails when workflow labels, configured
Java versions, setup order, README prerequisites, quality-gate documentation,
or project context disagree. The quality gate separately rejects execution when
the active Gradle Java is not version 17 or `FIREBASE_JAVA_HOME` is not a full
Java 21 installation. These runtime choices do not change Kotlin/JVM bytecode
targets, which remain Java 17.

Spotless is check-only in the gate; use `./gradlew spotlessApply` as an explicit
local formatting action. Detekt builds on its pinned default rules plus
`config/detekt/detekt.yml`. The baseline records reviewed pre-existing findings
only: new findings fail the gate, and resolved entries should be removed by
regenerating and reviewing `./gradlew detektBaseline` rather than hand-waving
new code into the file.

Gradle-resolved artifacts are locked and SHA-256 verified. Follow
`docs/dependency-updates.md` whenever a dependency, plugin, or wrapper version
changes; normal quality-gate runs use strict verification metadata.

## Reproducible CI caches

The workflow caches only pinned Android SDK 36, Build Tools 36.0.0, NDK
28.2.13676358, CMake 3.22.1, and CMake `FetchContent` sources. Android toolchain
keys contain every pinned version. The native-source key hashes the Gradle
wrapper and Whisper native build definitions. There are no broad restore
prefixes, so a changed version or defining
input cannot be accepted as a current exact cache hit.

When changing a tool version, update both the install command and cache key.
When changing Whisper native dependency declarations or CMake inputs, the hash
changes automatically. Increment the explicit `v1` cache namespace to invalidate an
otherwise-compatible cache deliberately. CI must still run the full gate after
any restore; a cache hit is an optimization, never validation evidence.

CI retains synthetic unit-test results, lint reports, the debug APK, the
self-service release-candidate validation APK, and debug/optimized
instrumentation APKs for seven days. The release-candidate artifact is for
physical validation only; it is not uploaded as a release. CI does not upload
app data, device logs, models, prompts, Chat databases, or media.

## Environment-only exclusions

Instrumentation execution requires a connected arm64 Android target and is not
run on the generic x86 GitHub runner. Host-native QuickJS execution proves the
shared engine and sandbox logic on Linux, but does not prove JNI symbol
resolution, ART integration, the arm64 binary, Android lifecycle behavior, or
device-specific timing/memory. GPU backend correctness, vendor drivers,
production-model Whisper JNI/LiteRT inference, memory pressure, and thermal
behavior are physical-device gates. They must not be inferred from a successful
CI build.

When a device check is skipped, record the reason and exact app/device/model
metadata in the physical-device result rather than marking the check as passed.
