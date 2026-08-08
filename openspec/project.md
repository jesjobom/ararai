# Project Context

## Purpose

ArarAI is an Android hub for configured open LLMs. It manages local model
artifacts and runs Chat inference on-device without requiring an application
backend, remote database, or hosted inference API. The project favors small,
validated increments and explicit runtime boundaries because mobile native
inference remains device-, driver-, model-, and workload-dependent.

## Current product

- The application starts at a Compose home hub with Chat, Models, and Settings
  destinations. Appearance can follow the system or use an
  explicitly selected light or dark theme.
- A checked-in static catalog defines every manageable model, artifact URL and
  hash, runtime, acceleration policy, input/reasoning capabilities, and default
  inference settings.
- Users can browse configured models in Chat and Transcription tabs,
  grouped by family and ordered from lighter to heavier artifacts. They can
  download, retry, cancel, update, delete, and select configured models.
  Downloaded model cards open workload-specific benchmarks, and models whose
  declared RAM requirement fits currently available device memory are marked as
  recommended. Downloads are resumable foreground data transfers with
  notification progress, and the selected model persists locally.
- The Chat supports streamed generation, cancellation, persistent and
  renameable sessions, per-model conversational context and temperature
  overrides, bounded context, selectable Markdown and local LaTeX math output, settings, and
  capability-gated reasoning, plus language-aware native speech playback for
  completed assistant responses.
- Assistant configuration groups mode-specific instructions, optional tools,
  and per-model conversational generation controls. LiteRT-LM total context is
  applied to its KV-cache capacity; unsupported independent response limits are
  disclosed rather than simulated. Runtime-backed last-turn metrics are
  ephemeral and benchmark runs retain fixed isolated parameters.
- Reasoning-only terminal generations are persisted as incomplete assistant
  responses. Normal Chat can show partial reasoning, while Voice Chat avoids
  empty TTS and recovers its loop.
- Structured prompts can contain text, normalized images, or recorded audio.
  Audio-capable models receive recordings directly. With a valid downloaded
  Whisper model, text-only models receive a locally produced transcript
  instead, and new audio messages persist transcript state for reconstructible
  context.
- Experimental Voice Chat runs contextual half-duplex turns for direct-audio
  models or text models backed by local Whisper transcription, compares offline
  WebRTC/Silero VAD and Android capture preprocessing, speaks streamed answers
  incrementally, and shares persisted conversations with normal Chat.
- Per-model diagnostics expose model/runtime metadata and local benchmark
  measurements.
- Conversations, preferences, models, runtime caches, and Chat media are local.
  Android backup and device transfer are disabled for the application.
- Settings exposes generated license and attribution metadata for the resolved
  Gradle graph plus reviewed disclosures for native and downloadable artifacts.

Model downloads contact only the artifact URLs declared in the catalog. A valid
local model is sufficient for the core Chat inference flow.

## Supported implementation baseline

- Namespace/application ID: `com.jesjobom.ararai`
- Android min SDK 28; compile and target SDK 36; arm64-v8a
- Kotlin 2.3.21; Jetpack Compose BOM 2026.06.00
- JDK 17; Android Gradle Plugin 9.2.x; Gradle 9.4.1
- Build Tools 36.0.0; Whisper uses NDK 28.2.13676358 and CMake 3.22.1
- LiteRT-LM 0.14.0 for configured Gemma 4 LiteRT-LM bundles
- whisper.cpp through JNI/NDK for transcription artifacts
- Bundled ML Kit Language ID 17.0.6 for offline response-language detection
- Android VAD 2.0.10 WebRTC/Silero adapters and ONNX Runtime Android 1.22.0 for
  experimental offline pause-detection comparison
- Plain SQLite for Chat sessions/messages and local preferences for model
  selection
- Debug builds/signing only

Exact dependency versions and Android settings come from the checked-in Gradle
configuration. Exact Gemma 4 and Whisper model support comes from
`app/src/main/res/raw/fixed_model.properties`. Acceleration and real-model
behavior require physical multi-turn memory, responsiveness, ANR, and thermal
evidence.
Resolved Gradle graphs are locked per project, downloaded artifacts are verified
against checked-in SHA-256 metadata, and the wrapper distribution checksum is
pinned. Intentional updates follow `docs/dependency-updates.md`.

## Architecture

Local inference is isolated behind `LocalLlmEngine`, backed by LiteRT-LM for the
configured Gemma 4 chat models. UI and ViewModels consume runtime-neutral generation
events and structured message content rather than JNI or LiteRT-specific types.

The principal boundaries are:

- `model/`: catalog parsing, selection, resolution, integrity, and download;
- application-scoped model download coordination is hosted by an Android
  foreground data-sync service so activity recreation does not own transfers;
- `engine/`: runtime-neutral contracts plus the LiteRT-LM adapter;
- `engine/` keeps retained-resource ownership and conversation-reuse policy
  separate from Android LiteRT SDK callbacks;
- `chat/`: session state, bounded history presentation and context construction,
  dispatcher-isolated SQLite persistence, streaming durability, media ownership,
  replaceable local audio transcription and
  direct-audio/transcript routing;
- `voice/`: contextual voice-loop coordination, PCM capture/VAD, experimental
  preprocessing and diagnostics, response segmentation, and sequential TTS;
- `ui/`: navigation and Compose presentation, with injectable adapters around
  a dedicated controller composition root that owns the shared local-LLM runtime,
  image import, audio recording/playback, response language identification,
  native text-to-speech, decoding, and draft cleanup;
- `benchmark/`: diagnostic state and inference measurement.

The native runtime may remain loaded across internal navigation when compatible,
but cancellation, replacement, and unload must retain clear ownership and
idempotent cleanup. Media files are app-owned and removed only when safe with
respect to remaining message references.

## Historical baseline

The project began as a single-screen, text-only llama.cpp/GGUF MVP with one
automatically downloaded configured model and no history, model picker, voice,
or image input. Those statements describe the first implementation slice only;
they are not current product constraints. Archived OpenSpec changes preserve
that history.

## Development and validation

Use TDD by default where an automated test is practical: establish the failing
behavior, implement the smallest complete change, then refactor without changing
the contract. Domain logic, ViewModels, persistence, catalog/download behavior,
media boundaries, and runtime ownership belong in automated tests.

`scripts/quality-gate.sh` is the common local/CI gate. It runs pinned Kotlin
formatting and static analysis, unit/Robolectric tests, lint, debug app and
instrumentation builds, and `openspec validate --all --strict`. Android
instrumentation execution requires a connected arm64 device:

```sh
./gradlew connectedDebugAndroidTest
```

Physical validation remains mandatory for real-model inference, actual GPU
backend selection/fallback, JNI/vendor behavior, lifecycle under load, memory,
thermal behavior, permissions, media/storage cleanup, and backup/transfer.
Follow `docs/device-validation.md`; never infer these results from a successful
CI build.

The OpenClaw container builds and validates source. After a debug build,
`scripts/copy-debug-apk.sh` copies the APK to
`/home/node/.openclaw/jarvis/artifacts/ararai/app-debug.apk` for installation in
the environment with ADB access.

## Specification workflow and precedence

1. `openspec/specs/local-llm-hub/spec.md` is the canonical consolidated product
   contract.
2. An approved active change under `openspec/changes/<name>/` defines its pending
   delta until it is archived and merged into the consolidated spec.
3. Source code, resources, manifests, Gradle files, and tests define exact
   implemented configuration and provide implementation evidence.
4. This project context and the README summarize those sources for maintainers;
   they do not supersede them.
5. Archived changes are historical records and must not be linked as active
   plans.

Before completing or archiving a change, review whether it materially changes
capabilities, architecture, setup, validation, privacy, or supported workflows.
If it does, update the README and this context in the same change. Documentation
must distinguish implemented automated evidence from checks that require a
physical device.

## Known constraints and open work

- The model catalog is static; arbitrary user-provided model entries are not
  supported.
- Release signing and a production release pipeline are not configured.
- The generic GitHub runner builds the instrumentation APK but does not execute
  device tests.
- Runtime compatibility and acceleration vary by device and require the
  versioned physical-device matrix.
- Export/import of conversations and a user-facing storage/privacy dashboard
  are potential future capabilities, not implemented features.
