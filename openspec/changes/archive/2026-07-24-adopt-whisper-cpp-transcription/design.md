## Context

The current Chat pipeline already persists audio transcript state, routes audio
directly to capable LLMs, converts audio to text for text-only models and keeps
the transcription engine behind `AudioTranscriber`. Its production adapter is
Android `SpeechRecognizer`, which depends on OEM behavior and language packs.
Physical tests on Samsung SM-S942W demonstrated that its external-audio API
cannot provide both low latency and complete results reliably.

ArarAI already builds llama.cpp with CMake/NDK for arm64. whisper.cpp also uses
GGML, so integrating both source trees into the same CMake graph risks target
and symbol collisions. Whisper additionally requires its own downloadable
model lifecycle and must coexist with memory-intensive LLM inference.

## Goals / Non-Goals

**Goals:**

- Produce deterministic, complete, local transcription from app-owned PCM WAV.
- Keep the existing engine-independent Chat domain and routing behavior.
- Select a practical multilingual quantized model using physical measurements.
- Manage the ASR model in the same user-facing local-model catalog while
  keeping runtime loading and feature selection type-safe.
- Bound native CPU/RAM concurrency and expose sanitized diagnostics.
- Make the native runtime replaceable and testable through Kotlin boundaries.

**Non-Goals:**

- Hosted transcription or network inference.
- Real-time microphone streaming or partial live captions.
- Speaker diarization, word timestamps or transcript editing in this change.
- Automatic fallback to Android `SpeechRecognizer`.
- Voice Chat session unification; this change prepares the shared transcriber
  but does not yet change the stateless Voice Chat specification.

## Decisions

### Validate model/runtime choices with a physical-device spike first

The first implementation task builds an isolated whisper.cpp prototype and
compares multilingual quantized `tiny`, `base` and `small` candidates on Samsung
SM-S942W using representative pt-BR clips. The report records model bytes,
load time, transcription time/real-time factor, peak process memory, output
quality and cancellation behavior. The checked-in default is selected only
after this evidence exists.

Alternative: select `base` immediately. Rejected because its quality and the
coexistence cost beside the selected LLM have not been measured in the app.

### Isolate whisper.cpp in a dedicated Android library module

A `whisper-runtime` Android library owns a pinned whisper.cpp revision, its own
CMake graph and a narrow JNI surface. Native symbols use hidden visibility and
the module exposes only wrapper entry points required by Kotlin. This avoids
combining whisper.cpp and llama.cpp GGML targets in one CMake graph and reduces
symbol-interposition risk when both shared libraries are loaded.

The JNI boundary accepts the validated model path and mono 16 kHz float/PCM
samples, supports cancellation, returns final text plus timing metadata and
releases every native context deterministically.

Alternative: link whisper.cpp into `ararai_llama`. Rejected because the two
pinned projects can carry incompatible GGML revisions and duplicate targets or
symbols.

### Unify managed artifacts without unifying their runtimes

The checked-in catalog describes Chat and utility artifacts through shared
identity, download, integrity, storage and resource metadata. Typed purposes
and tasks distinguish `CHAT`, `REASONING` and `UTILITY` use from tasks such as
`TRANSCRIPTION`; typed runtime and artifact-format metadata select the proper
loader. Whisper therefore appears in the existing Model Manager with a visible
`Utility` badge and `Audio transcription` task, but Chat and benchmark selectors
query only compatible Chat models.

The catalog abstraction does not force ASR and LLM artifacts into one runtime
shape. LLM inference settings are required only by Chat/Reasoning artifacts,
while Whisper-specific settings belong to its runtime adapter. Download,
integrity validation, progress, retry, update, atomic promotion and safe
deletion remain shared.

Before selection, the catalog exposes multilingual quantized `tiny`, `base`
and `small` candidates with `Experimental` maturity. Each candidate can be
downloaded, deleted and invoked by the transcription benchmark, but neither is
the production default. The physical comparison promotes one candidate to the
stable transcription role; the other is removed from the default catalog or
retained only as an explicitly advanced alternative based on measured value.

The model is not packaged in the APK and is downloaded only after an explicit
user action. Chat explains when transcription requires the model and links to
the applicable setup action. Invalid or partial artifacts never become
available to the runtime.

### Use Whisper as the sole production transcriber

`WhisperCppAudioTranscriber` implements `AudioTranscriber`. Android
`SpeechRecognizer` is removed from production wiring after the Whisper
acceptance gate passes. There is no silent fallback because different engines
would produce device-dependent history and context.

The existing persisted transcript schema remains compatible. New diagnostics
identify Whisper revision, model ID, execution duration, audio duration,
real-time factor, thread count and failure category without including audio or
recognized text.

### Serialize heavy ASR and LLM native work initially

A process-scoped coordinator prevents Whisper and LLM inference from executing
concurrently until physical measurements prove a safe device-independent
policy. For an audio-capable LLM, direct-audio generation has priority and
Whisper enrichment runs after generation. For a text-only LLM, Whisper runs
first, releases its active native context, and only then may LLM generation
start.

This sacrifices parallel enrichment latency to protect response latency,
memory headroom and thermal stability. The coordinator boundary permits a
future measured concurrency policy without changing Chat or the transcriber.

### Treat full-file final output as the success contract

The runtime decodes the entire validated WAV and returns one normalized final
transcript. Empty output is a typed failure. Cancellation stops native work and
must not persist a completed transcript. Existing pending/completed/failed
states and transcript visibility remain unchanged.

## Risks / Trade-offs

- **Native GGML symbol or build collision** → isolate the runtime module, hide
  symbols and add an APK load smoke test with both llama and Whisper libraries.
- **ASR plus LLM exceeds memory headroom** → serialize workloads initially,
  release active Whisper contexts before LLM generation and measure peak RAM.
- **Chosen model is too slow or inaccurate** → benchmark three candidates and
  keep model selection configuration-driven.
- **Model download increases storage/network use** → explicit download,
  integrity metadata, size disclosure and user-controlled deletion/retry.
- **JNI cancellation leaks or crashes** → native ownership tests, idempotent
  close, cancellation stress tests and physical navigation/cancel testing.
- **APK grows from native code** → ship only arm64 and keep model weights out of
  the APK; report the native size delta before acceptance.
- **Serialized enrichment completes later** → prioritize direct-audio response;
  keep the message pending until safe enrichment finishes.

## Migration Plan

1. Archive the Android-recognizer OpenSpec without promoting its engine-specific
   requirements; preserve reusable uncommitted domain work.
2. Run the isolated physical spike and select/pin runtime plus default model.
3. Generalize the model catalog and Model Manager with typed purpose/task
   metadata while preserving Chat-only selection.
4. Add the runtime module, JNI boundary and JVM-testable Kotlin adapter.
5. Add the selected Whisper artifact to the unified catalog and setup UI.
6. Wire the workload coordinator and replace production transcriber wiring.
7. Validate text-only and audio-capable routes, cancellation, process restart,
   model deletion and simultaneous library loading.
8. Remove Android recognizer code and its engine-specific diagnostics only
   after Whisper passes the physical acceptance gate.

Rollback keeps the engine-independent transcript schema and disables local
transcription; it does not reactivate the Android recognizer automatically.

## Open Questions

- Which of `tiny`, `base` or `small` gives the best quality/latency trade-off?
- What thread count gives the best latency without materially delaying or
  heating subsequent LLM inference on the target device?
- Can the selected Whisper model remain memory-mapped between turns within the
  measured memory budget, or must every transcription fully unload it?
