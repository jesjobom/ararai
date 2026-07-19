# ArarAI quality gates

## Coverage inventory

- JVM/Robolectric (`app/src/test`): model catalog/download/integrity, Chat state
  and persistence, prompt construction, engine orchestration, streaming durability,
  media ownership/import limits, LiteRT ownership, and backup policy.
- Android instrumentation (`app/src/androidTest`): runtime permission/backup
  manifest configuration, real `ContentResolver` image import, MainActivity
  stop/resume, and JNI library/symbol loading with safe null-handle operations.
- Native build: `assembleDebug` compiles and packages the pinned llama.cpp JNI
  library for arm64-v8a; the instrumentation JNI smoke verifies device loading.
- Physical device (`docs/device-validation.md`): real model inference, backend
  selection/fallback, cancellation, repeated runs, lifecycle, memory, thermal,
  permissions, storage cleanup, backup, and device transfer.

## Required automated gate

`scripts/quality-gate.sh` is the local and CI entry point. It runs:

1. `spotlessCheck` with Spotless 7.2.1 and ktlint 1.7.1
2. `detekt` 1.23.8 with the reviewed baseline in `config/detekt/baseline.xml`
3. `testDebugUnitTest`
4. `lintDebug`
5. `assembleDebug`
6. `assembleDebugAndroidTest`
7. `openspec validate --all --strict`

Any failure makes the gate fail. GitHub Actions runs the same script for pull
requests, pushes to `main`, and manual dispatches. Toolchain inputs are pinned to
JDK 17, Android 36, Build Tools 36.0.0, NDK 28.2.13676358, CMake 3.22.1, Gradle
9.4.1 through the wrapper, and OpenSpec 1.6.0.

Spotless is check-only in the gate; use `./gradlew spotlessApply` as an explicit
local formatting action. Detekt builds on its pinned default rules plus
`config/detekt/detekt.yml`. The baseline records reviewed pre-existing findings
only: new findings fail the gate, and resolved entries should be removed by
regenerating and reviewing `./gradlew detektBaseline` rather than hand-waving
new code into the file.

## Reproducible CI caches

The workflow caches only pinned Android SDK 36, Build Tools 36.0.0, NDK
28.2.13676358, CMake 3.22.1, and CMake `FetchContent` sources. Android toolchain
keys contain every pinned version. The native-source key hashes the Gradle
wrapper, Android build definition, root CMake definition, and host-toolchain
inputs. There are no broad restore prefixes, so a changed version or defining
input cannot be accepted as a current exact cache hit.

When changing a tool version, update both the install command and cache key.
When changing native dependency declarations or CMake inputs, the hash changes
automatically. Increment the explicit `v1` cache namespace to invalidate an
otherwise-compatible cache deliberately. CI must still run the full gate after
any restore; a cache hit is an optimization, never validation evidence.

CI retains synthetic unit-test results, lint reports, the debug APK, and the
instrumentation APK for seven days. It does not upload app data, device logs,
models, prompts, Chat databases, or media.

## Environment-only exclusions

Instrumentation execution requires a connected arm64 Android target and is not
run on the generic x86 GitHub runner. GPU backend correctness, vendor drivers,
production-model JNI/LiteRT inference, memory pressure, and thermal behavior are
physical-device gates. They must not be inferred from a successful CI build.

When a device check is skipped, record the reason and exact app/device/model
metadata in the physical-device result rather than marking the check as passed.
