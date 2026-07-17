# Project Context

## Purpose

ArarAI is an Android hub for configured open LLMs. It manages local model
artifacts and runs Chat inference on-device without requiring an application
backend, remote database, or hosted inference API. The project favors small,
validated increments and explicit runtime boundaries because mobile native
inference remains device-, driver-, model-, and workload-dependent.

## Current product

- The application starts at a Compose home hub with Chat, Models, and
  Diagnostics destinations.
- A checked-in static catalog defines every manageable model, artifact URL and
  hash, runtime, acceleration policy, input/reasoning capabilities, and default
  inference settings.
- Users can download, retry, cancel, update, delete, and select configured
  models. The selected model persists locally.
- The Chat supports streamed generation, cancellation, persistent and
  renameable sessions, bounded context, Markdown output, settings, and
  capability-gated reasoning.
- Structured prompts can contain text, normalized images, or recorded audio
  when the selected model declares the corresponding input capability.
- Diagnostics expose model/runtime metadata and local benchmark measurements.
- Conversations, preferences, models, runtime caches, and Chat media are local.
  Android backup and device transfer are disabled for the application.

Model downloads contact only the artifact URLs declared in the catalog. A valid
local model is sufficient for the core Chat inference flow.

## Supported implementation baseline

- Namespace/application ID: `com.jesjobom.ararai`
- Android min SDK 28; compile and target SDK 36; arm64-v8a
- Kotlin 2.3.21; Jetpack Compose BOM 2026.06.00
- JDK 17; Android Gradle Plugin 9.2.x; Gradle 9.4.1
- Build Tools 36.0.0; NDK 28.2.13676358; CMake 3.22.1
- llama.cpp through JNI/NDK for GGUF artifacts
- LiteRT-LM 0.14.0 for configured LiteRT-LM bundles
- Plain SQLite for Chat sessions/messages and local preferences for model
  selection
- Debug builds/signing only

Exact dependency versions and Android settings come from the checked-in Gradle
configuration. Exact model support comes from
`app/src/main/res/raw/fixed_model.properties`.

## Architecture

Local inference is isolated behind `LocalLlmEngine`. Runtime selection is driven
by catalog metadata, with `ConfiguredLocalLlmEngine` dispatching to llama.cpp or
LiteRT-LM implementations. UI and ViewModels consume runtime-neutral generation
events and structured message content rather than JNI or LiteRT-specific types.

The principal boundaries are:

- `model/`: catalog parsing, selection, resolution, integrity, and download;
- `engine/`: runtime-neutral contracts plus llama.cpp and LiteRT-LM adapters;
- `chat/`: session state, context construction, SQLite persistence, streaming
  durability, and media ownership;
- `ui/`: navigation and Compose presentation, with injectable adapters around
  image import, audio recording/playback, decoding, and draft cleanup;
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

`scripts/quality-gate.sh` is the common local/CI gate. It runs unit/Robolectric
tests, lint, debug app and instrumentation builds, and `openspec validate --all
--strict`. Android instrumentation execution requires a connected arm64 device:

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
