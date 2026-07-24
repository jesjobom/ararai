## Why

Android on-device `SpeechRecognizer` cannot provide reliable full-file Chat
transcription across supported devices: physical testing on Samsung SM-S942W
showed that real-time input is slow while fast external-audio input can return
only an initial fragment as a successful result. ArarAI needs a deterministic,
fully local transcriber whose model, execution and diagnostics it controls.

## What Changes

- Replace Android `SpeechRecognizer` as the production Chat transcription path
  with a pinned `whisper.cpp` runtime behind the existing `AudioTranscriber`
  boundary.
- Manage a multilingual Whisper model as a validated, app-owned, downloadable
  `UTILITY` artifact in the existing Model Manager instead of embedding it in
  the APK or exposing it as a selectable Chat model.
- Generalize the local-model catalog with typed purposes, tasks, runtimes and
  artifact formats so future OCR, embedding, reranking, TTS and other utility
  models can reuse model download and storage management.
- Benchmark candidate quantized models on the target physical device before
  selecting the checked-in default.
- Preserve persisted audio transcripts, synchronous text-only routing,
  asynchronous enrichment for audio-capable LLMs, transcript visibility and
  copyable diagnostics.
- Coordinate Whisper and LLM native workloads so transcription cannot create
  uncontrolled CPU/RAM contention or delay direct-audio responses.
- Remove Android speech-service availability, language-pack state and PCM pipe
  pacing from the product capability contract. No hosted transcription or
  automatic fallback to the Android recognizer is permitted.

## Capabilities

### New Capabilities

- `local-audio-transcription`: Managed whisper.cpp runtime, ASR model lifecycle,
  local execution, resource coordination, diagnostics and physical validation.

### Modified Capabilities

- `local-llm-hub`: Persist transcripts with Chat audio and route recorded audio
  through direct-audio asynchronous enrichment or synchronous transcription for
  text-only models.

## Impact

- Native build: pinned whisper.cpp CMake dependency, arm64 JNI bridge and native
  lifecycle/cancellation handling.
- Model management: a unified managed-artifact catalog with typed purpose and
  task metadata, shared download/integrity state and runtime-specific loading.
- Chat: production transcriber wiring, audio capability gating, workload
  scheduling, persisted diagnostics and error presentation.
- APK size increases by the native runtime but not by the selected Whisper model.
- Existing Android-transcriber code becomes removable after the Whisper path
  passes automated and physical-device acceptance gates.
