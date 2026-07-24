# ArarAI

ArarAI is an Android application for running configured open LLMs locally on an
arm64 device. Models, inference, conversations, and Chat media stay app-owned;
the core Chat flow does not depend on a remote inference API, application
backend, or external database.

## Current capabilities

- Checked-in model catalog with download, integrity validation, selection,
  update, deletion, and persisted active-model choice.
- Runtime selection through a shared `LocalLlmEngine` boundary:
  - llama.cpp/JNI for GGUF models;
  - LiteRT-LM for configured `.litertlm` bundles.
- Streamed local Chat with persistent and renameable sessions, cancellation,
  bounded context construction, selectable Markdown and local LaTeX math rendering, and optional reasoning
  controls when declared by the selected model.
- Offline response-language detection and language-aware native Android
  text-to-speech playback for completed assistant responses.
- Experimental stateless Voice Chat v0 for audio-capable LiteRT-LM models. It
  compares offline WebRTC/Silero voice-activity detection, Android capture
  sources, and optional native noise suppression, then speaks streamed answers
  in ordered segments. Voice turns, audio, responses, and diagnostics are not
  persisted and no context is carried between turns.
- Capability-gated image and recorded-audio prompts. Chat uses a downloaded,
  integrity-validated whisper.cpp model for local transcription. Audio-capable
  models receive the original audio while transcription runs asynchronously;
  text-only models receive the transcript after synchronous recognition.
  Imported images are normalized and recordings remain app-owned Chat media.
- Diagnostics for model/runtime identity and local inference performance.
- Resumable model downloads continue under an Android foreground service with
  progress and cancellation in a persistent notification.
- Home, Chat, Voice Chat v0, Models, Diagnostics, and Settings destinations implemented with
  Jetpack Compose.
- Persistent System, Light, and Dark appearance selection with Material dynamic
  colors on supported devices.

The checked-in catalog is authoritative for models the UI can manage. A feature
being present in the UI does not prove that every model or device supports it;
input and reasoning controls follow model capability metadata, and real GPU,
memory, thermal, and multimodal behavior require physical-device validation.

## Platform and toolchain

- Application ID: `com.jesjobom.ararai`
- Android: min SDK 28, compile/target SDK 36, arm64-v8a
- Kotlin 2.3.21 and Compose BOM 2026.06.00
- JDK 17, AGP 9.2.x, Gradle wrapper 9.4.1
- Build Tools 36.0.0, NDK 28.2.13676358, CMake 3.22.1
- Local runtimes: llama.cpp through JNI/NDK and LiteRT-LM 0.14.0
- Bundled ML Kit Language ID 17.0.6
- Android VAD 2.0.10 (WebRTC and Silero) plus ONNX Runtime Android 1.22.0
  transitively for the experimental Silero comparison
- Debug signing/builds only; release signing is not configured

Model definitions and their runtime, artifact, capability, integrity, and
inference metadata live in `app/src/main/res/raw/fixed_model.properties`.

## Build and verification

The supported local and CI entry point is:

```sh
scripts/quality-gate.sh
```

It runs pinned Kotlin formatting and static analysis, JVM/Robolectric tests,
Android lint, the debug app build, the debug instrumentation APK build, and
strict validation of every OpenSpec change. The same command is used by
`.github/workflows/android-quality-gate.yml`.

Individual commands remain useful while iterating:

```sh
./gradlew spotlessCheck detekt
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
./gradlew assembleDebugAndroidTest
openspec validate --all --strict
```

Use `./gradlew spotlessApply` to format Kotlin sources locally. Detekt's
reviewed legacy baseline and rule configuration live under `config/detekt/`;
new findings are not added to the baseline as a routine fix. CI cache inputs
and invalidation rules are documented in `docs/quality-gates.md`.

With an authorized arm64 Android device connected, execute the instrumentation
suite:

```sh
./gradlew connectedDebugAndroidTest
```

The generic CI runner compiles but does not execute this suite. Follow
`docs/device-validation.md` for real-model, GPU/backend, lifecycle, permission,
storage, memory, and thermal checks. `docs/quality-gates.md` defines what each
automated layer does and does not prove.

## APK handoff

After `./gradlew assembleDebug`, copy the APK to the shared, non-versioned
handoff location:

```sh
scripts/copy-debug-apk.sh
```

The resulting file is
`/home/node/.openclaw/jarvis/artifacts/ararai/app-debug.apk`. Install and test it
from the environment that has ADB access.

## Local data and privacy

ArarAI stores conversations in local SQLite, Chat media and downloaded models
in app-owned files, model-selection preferences locally, and runtime caches on
the current device. Android cloud backup and device-to-device transfer are
disabled for the whole application. Reinstalling the app or moving to another
device therefore does not restore conversations, media, models, or settings.

Model downloads still require network access to their configured artifact URLs.
Once a valid model is present, core inference and Chat do not call a hosted
inference service.

Chat audio transcription uses only the bundled whisper.cpp runtime with an
explicitly downloaded local model; there is no hosted or Android
speech-recognizer fallback. The Model Manager offers Base Q5_1 (256 MB free RAM
recommended) and Small Q5_1 (512 MB recommended). Base is preferred when both
are installed, while Small is used when it is the only valid Whisper model.
Completed transcripts remain persisted and available to conversation context
even when the Chat preference hides them from message presentation. Older
audio messages remain playable but are not transcribed retroactively. Failed
transcriptions offer a copyable, sanitized in-app diagnostic report with model,
timing, thread and failure metadata; reports exclude audio bytes and recognized
speech.

Voice Chat v0 keeps only bounded timing/configuration diagnostics in memory.
Its temporary WAV is deleted after each exchange, cancellation, or failure and
stale Voice Chat temporary files are reconciled on startup. It does not write
voice messages or generated text to the Chat database.

## Planning and sources of truth

Project planning and requirements live under `openspec/`:

- `openspec/specs/local-llm-hub/spec.md` is the canonical consolidated product
  specification.
- `openspec/changes/<change-name>/` contains proposed or in-progress deltas.
- `openspec/changes/archive/` contains historical decisions, not active work.
- `openspec/project.md` explains the current product and engineering context.

When sources disagree, use the consolidated spec for product requirements, an
active approved change for its not-yet-archived delta, and checked-in build
configuration/source for exact implementation details. The README is onboarding
documentation and must not override those sources.

Before completing or archiving a change that affects capabilities,
architecture, setup, validation, privacy, or supported workflows, review this
README and `openspec/project.md`. Claims must describe implemented and verified
behavior; device-dependent behavior must remain identified as such.
