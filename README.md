# ArarAI

![ArarAI project banner](docs/images/ararai-banner.webp)

ArarAI is an Android application for running configured open LLMs locally on an
arm64 device. Models, inference, conversations, and Chat media stay app-owned;
the core Chat flow does not depend on a remote inference API, application
backend, or external database.

## Current capabilities

- Checked-in model catalog organized into Chat and Transcription tabs,
  with family-preserving light-to-heavy ordering, available-memory
  recommendations, download, integrity validation, selection, update, deletion,
  and persisted active-model choice.
- No model download starts automatically on first launch. When no Chat model is
  local, a one-time prompt shows the default model and approximate size before
  offering download, Model Management, or dismissal. Normal Chat history stays
  accessible with submission disabled; Voice Chat stays unavailable until a
  Chat model is local.
- Gemma 4 E2B and E4B Chat inference through LiteRT-LM behind a shared
  `LocalLlmEngine` boundary. E2B is the default lower-resource option; both
  configured models support text, image, audio, and reasoning input.
- Streamed local Chat with persistent and renameable sessions, cancellation,
  bounded and incrementally loadable history, bounded context construction,
  selectable Markdown and local LaTeX math rendering, and optional reasoning
  controls when declared by the selected model.
- Offline response-language detection and language-aware native Android
  text-to-speech playback for completed assistant responses.
- Experimental contextual Voice Chat for audio-capable models or text models
  backed by local Whisper transcription. It
  compares offline WebRTC/Silero voice-activity detection, Android capture
  sources, and optional native noise suppression, then speaks streamed answers
  in ordered segments. Voice turns, app-owned audio, completed transcripts, and
  responses use the same persisted conversation history as normal Chat. For
  image-capable models, Voice Chat can open an in-app camera while the microphone
  and VAD keep running. Camera interactions restart the trailing-silence window;
  the user can fix a photo manually or let the next valid pause capture the
  current frame automatically for that audio turn or its local transcript.
- Capability-gated image and recorded-audio prompts. Chat uses a downloaded,
  integrity-validated whisper.cpp model for local transcription. Audio-capable
  models receive the original audio while transcription runs asynchronously;
  text-only models receive the transcript after synchronous recognition.
  Gallery images and in-app camera photos are normalized, and recordings remain
  app-owned Chat media.
- Per-downloaded-model diagnostics for runtime identity and local inference or
  transcription performance.
- Resumable model downloads continue under an Android foreground service with
  progress and cancellation in a persistent notification.
- Home, Chat, Voice Chat, Models, Assistant configuration, and Settings destinations implemented with
  Jetpack Compose; model benchmarks are opened from downloaded model cards.
- Persistent System, Light, and Dark appearance selection with Material dynamic
  colors on supported devices.
- A localized Settings disclosure lists resolved direct and transitive Gradle
  library licenses together with reviewed native-runtime and downloadable-model
  notices.
- Optional Wikipedia knowledge retrieval for the validated Gemma 4 E2B and E4B
  bundles in normal Chat and Voice Chat. The model selects structured calls
  semantically; successful answers retain bounded canonical source links in the
  shared local conversation.

The checked-in catalog is authoritative for models the UI can manage. A feature
being present in the UI does not prove that every model or device supports it;
input and reasoning controls follow model capability metadata, and real GPU,
memory, thermal, and multimodal behavior require physical-device validation.

## Platform and toolchain

- Application ID: `com.jesjobom.ararai`
- Android: min SDK 28, compile/target SDK 36, arm64-v8a
- Kotlin 2.3.21 and Compose BOM 2026.06.00
- JDK 17 for Android Gradle builds, AGP 9.2.x, Gradle wrapper 9.4.1
- Build Tools 36.0.0; Whisper uses NDK 28.2.13676358 and CMake 3.22.1
- LLM runtime: LiteRT-LM 0.14.0; transcription runtime: whisper.cpp
- Bundled ML Kit Language ID 17.0.6
- Android VAD 2.0.10 (WebRTC and Silero) plus ONNX Runtime Android 1.22.0
  transitively for the experimental Silero comparison
- Debug builds use debug signing. Release builds require an external upload
  keystore and password files; no signing material is stored in this repository.
- Production release and locally signed release-candidate builds use optimized
  R8/resource shrinking and verify mapping, seeds, usage, and configuration
  outputs. Physical smoke validation remains mandatory before delivery; see
  `docs/release-shrinking.md`.

Model definitions and their runtime, artifact, acceleration, capability,
integrity, and inference metadata live in
`app/src/main/res/raw/fixed_model.properties`. The catalog supports Gemma 4
LiteRT-LM for Chat and Whisper for transcription; it does not ship a GGUF chat
runtime. Real acceleration, memory, responsiveness, ANR, and thermal behavior
still require physical-device validation.

## Build and verification

The supported local and CI entry point is:

```sh
scripts/quality-gate.sh
```

It runs Firestore Security Rules tests in the local Emulator Suite, pinned
Kotlin formatting and static analysis, JVM/Robolectric tests, Android lint, the
debug app build, the debug instrumentation APK build, and strict validation of
every OpenSpec change. Gradle/AGP builds require JDK 17; Firebase Emulator tests require a full JDK 21.
Set `JAVA_HOME` to JDK 17 and `FIREBASE_JAVA_HOME` to JDK 21 before invoking the
gate. The Firebase toolchain also requires Node.js 22+. The same command is used by
`.github/workflows/android-quality-gate.yml`.

Individual commands remain useful while iterating:

```sh
npm ci --include=dev --ignore-scripts
FIREBASE_JAVA_HOME=/path/to/jdk-21 scripts/run-firestore-rules-tests.sh
scripts/check-java-runtime-alignment.sh
./gradlew spotlessCheck detekt
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
./gradlew assembleDebugAndroidTest
./gradlew assembleReleaseCandidate
scripts/verify-release-artifacts.sh releaseCandidate
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

## Signed Play release

Release signing reads the upload keystore and password files from an external
environment file. The supported entry point resolves that file relative to the
repository, validates the secret-file permissions, builds and verifies the
signed bundle, and copies it to the shared artifacts area:

```sh
scripts/build-release-bundle.sh
```

The expected environment file is
`../../secrets/ararai/release-signing.env`, relative to the repository root. It
defines `ARARAI_UPLOAD_STORE_FILE`, `ARARAI_UPLOAD_STORE_PASSWORD_FILE`,
`ARARAI_UPLOAD_KEY_ALIAS`, and `ARARAI_UPLOAD_KEY_PASSWORD_FILE`. The concrete
host paths remain outside this repository.

The password files must contain only their respective password. Keep the
keystore and password files outside the repository with restrictive filesystem
permissions and maintain an encrypted backup outside the build host. Google
Play App Signing owns the distribution signing key; this local key is the upload
key and must not be reused for other applications.

## Local data and privacy

The [privacy policy](docs/privacy.md) applies to the official Google Play
distribution published by Jesjobom. Generated-content reporting is an explicit,
user-initiated exception to local-only conversation handling: only the selected
assistant response, individually approved textual context, reason, optional
comment, and bounded allowlisted metadata are sent to the official Firebase
project. Raw image/audio media, reasoning, hidden instructions, paths, and
unselected history are excluded. Operational controls are documented in
[`docs/reporting-operations.md`](docs/reporting-operations.md), and the proposed
Google Play declaration is maintained in
[`docs/google-play-data-safety.md`](docs/google-play-data-safety.md).

When GitHub Pages is enabled from the `main` branch `/docs` folder, the public
policy is available at <https://jesjobom.github.io/ararai/privacy/>.

Forks and third-party distributions must use independent backend configuration,
privacy disclosures, operational ownership, and Google Play Data safety answers;
the official report service is not a shared backend for derived builds.

ArarAI stores conversations in local SQLite, Chat media and downloaded models
in app-owned files, model-selection preferences locally, and runtime caches on
the current device. Android cloud backup and device-to-device transfer are
disabled for the whole application. Reinstalling the app or moving to another
device therefore does not restore conversations, media, models, or settings.

Model downloads still require network access to their configured artifact URLs.
Once a valid model is present, core inference and Chat do not call a hosted
inference service.

The optional **Local calculator** under **Assistant configuration → Tools** is
disabled by default and runs entirely on-device. It is advertised only to the
individually validated Gemma bundles. The bounded grammar supports decimal and
scientific literals, parentheses, unary signs, `+`, `-`, `*`, `/`, `%`, bounded
`^`, `abs`, `sqrt`, `min`, `max`, `ln`, `log10`, `sin`, `cos`, `tan`, and the
`pi`/`e` constants; trigonometric arguments use radians. Evaluation uses
DECIMAL128. Division may be rounded, and transcendental or fractional-power
results are labeled approximate. Expressions and intermediate results are not
sent over the network or persisted as conversation messages.

Wikipedia is an explicit opt-in exception to otherwise local Chat processing.
Enable it under **Assistant configuration → Tools**. A request is sent only when the
selected installed model advertises `wikipedia_search` and the model emits that
structured call for the current turn. Enabling the option does not send data by
itself and does not guarantee that every factual prompt will trigger research.

Each eligible call sends only the model-selected query and a validated
Wikipedia language code to the corresponding official MediaWiki HTTPS endpoint. ArarAI does
not send the conversation history, system instruction, session identifier,
audio, image, or local model data. The provider allows up to three calls per
user turn, rejects redirects and non-Wikipedia URLs,
applies a 12-second total deadline and bounded response/context limits, and
returns at most three sources. Wikipedia content is external untrusted
reference material and is not guaranteed to be current or complete.

Completed assistant answers persist only bounded source metadata: provider,
title, canonical URL, language, and retrieval time. Raw extracts, MediaWiki
JSON, and LiteRT-LM tool protocol are not stored or rendered. Normal Chat shows
the source links with the answer. Voice Chat keeps microphone capture inactive
during research, speaks only the final answer, and leaves the same sources
visible when the shared conversation is opened in normal Chat. Offline,
timeout, cancellation, and provider failures remain controlled outcomes.

The current networking and storage contract, automated evidence, and extension
rules for future skills are documented in `docs/wikipedia-skill.md`.

Debug builds also expose experimental **Tavily** and **Exa** cards under
**Assistant configuration → Tools**. Each provider is disabled and
unconfigured by default. The user must supply their own provider token and pass
a direct smoke test before the provider is selected. ArarAI encrypts these
tokens with an Android Keystore key, never displays them again, and excludes
them from model context, conversation history, logs, diagnostics, backup, and
export. Clearing app data or removing the credential deletes the stored token.

Either or both experimental web providers can be enabled. When both are
enabled, Exa runs first and Tavily is used only after a controlled provider
failure. Both implement the same
model-visible `web_search` schema and return untrusted query-focused excerpts,
not a provider-generated final answer: at most three HTTPS sources, two
500-character excerpts per source, 1,800 characters for the complete reference
envelope, and two calls per user turn. Tavily and Exa remain experimental until
the checked-in comparison produces an explicit approval verdict. Release builds
do not advertise unapproved web search to the model. See
`docs/web-search-providers.md`.

Chat audio transcription uses only the bundled whisper.cpp runtime with an
explicitly downloaded local model; there is no hosted or Android
speech-recognizer fallback. The Model Manager offers Base Q5_1 (256 MB free RAM
recommended) and Small Q5_1 (512 MB recommended). Base is preferred when both
are installed, while Small is used when it is the only valid Whisper model.
Completed transcripts remain persisted and available to conversation context
even when the Chat preference hides them from message presentation. Older
audio messages remain playable but are not transcribed retroactively. Failed
transcriptions show a concise error without exposing internal runtime
diagnostics in Chat.

Normal Chat and Voice Chat share one current persisted conversation. A
compatible LiteRT-LM native conversation is reused incrementally, so subsequent
turns send only new user content. Persisted history remains the recoverable
source of truth: after runtime recreation or process restart, the app initializes
a fresh native conversation once from bounded reconstructible history. Runtimes
without incremental conversation support receive the bounded reconstructed
prompt on each turn.

Voice Chat keeps only bounded timing/configuration diagnostics in memory.
Temporary capture WAV files are deleted after each exchange, cancellation, or
failure, while the app-owned conversation copy remains available for replay.
Stale Voice Chat temporary files are reconciled on startup. A completed
transcript reconstructs an audio turn after native-state loss; the app omits
rather than fabricates content for legacy or failed audio without a transcript.

Assistant configuration also has a **Generation** tab. Context window and
temperature overrides are saved independently for each Chat model and apply to
future normal Chat and Voice Chat turns. Context window configures LiteRT-LM's
total input-plus-output capacity and may reload the engine. Temperature offers
Precise, Balanced, Creative, and manual values. LiteRT-LM 0.14.0 does not expose
an independent output-token limit, so the app reports that limitation instead
of presenting a control it cannot enforce. Runtime-backed metrics from the last
conversational turn are shown when available; benchmark parameters and metrics
remain fixed and isolated.

The **Audio** tab controls the global language hint for local Whisper
transcription in both Chat modes. Automatic detection is the default; users can
instead follow the Android system language, follow the ArarAI interface, or fix
transcription to English or Portuguese. Changes affect only new transcriptions.

If a reasoning-enabled generation finishes without final answer text, ArarAI
persists and displays an explicit incomplete response. Partial reasoning remains
available in normal Chat when Show reasoning is enabled. Voice Chat does not
speak empty output and recovers the loop after recording the incomplete turn.

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
