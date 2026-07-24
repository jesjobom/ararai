## 1. Physical-device spike and selection

- [x] 1.1 Pin a candidate whisper.cpp revision and document its license and source provenance.
- [ ] 1.2 Build an isolated arm64 Android spike that loads both Whisper and existing LLM native libraries.
- [x] 1.3 Add a repeatable pt-BR corpus runner for multilingual quantized `tiny`, `base` and `small` candidates with selectable CPU thread counts.
- [ ] 1.4 Measure artifact/APK size, load time, transcription time, real-time factor, peak process RAM, cancellation and output quality on Samsung SM-S942W.
- [ ] 1.5 Record the comparison verdict and select the default model, thread count and native-context retention policy.
- [x] 1.6 Add verified multilingual quantized `tiny`, `base` and `small` candidates to the Model Manager as experimental utilities.
- [x] 1.8 Compile the physical benchmark runtime with `-O3`, ARMv8.2 dot-product and FP16 instead of measuring the unoptimized native debug default.
- [x] 1.7 Add an on-device candidate test action and copyable comparison report using the same recordings.

## 2. Unified managed-model catalog

- [x] 2.1 Add typed model purposes and tasks with backward-compatible Chat defaults.
- [x] 2.2 Allow runtime-specific model metadata without requiring LLM inference settings for utility artifacts.
- [x] 2.3 Restrict Chat selection, bootstrap and benchmark flows to compatible Chat models.
- [x] 2.4 Show purpose/task badges and manage utility downloads in the existing Model Manager.
- [x] 2.5 Add parser, validation, selection and presentation tests for mixed Chat/utility catalogs.

## 3. Isolated whisper.cpp runtime

- [x] 3.1 Create the dedicated `whisper-runtime` Android library with an independent CMake graph and hidden native symbols.
- [ ] 3.2 Implement the narrow JNI lifecycle for model load, full-file decode, cancellation and deterministic release.
- [ ] 3.3 Implement WAV-to-Whisper sample conversion with strict format validation and bounded allocations.
- [ ] 3.4 Add native/Kotlin failure mapping and sanitized runtime timing metadata.
- [ ] 3.5 Add automated lifecycle, invalid-input, empty-output and cancellation tests without requiring model weights in JVM tests.

## 4. Whisper model lifecycle

- [ ] 4.1 Promote the selected Whisper candidate in the unified catalog with purpose `UTILITY`, task `TRANSCRIPTION`, URL, path, bytes, SHA-256, language and resource metadata.
- [ ] 4.2 Reuse temporary download, integrity validation and atomic promotion through the shared model-management lifecycle.
- [ ] 4.3 Add explicit download, progress, retry, update and safe deletion flows for the Whisper artifact.
- [ ] 4.4 Reconcile stale Whisper temporary files without touching LLM models, Chat media or valid transcripts.
- [ ] 4.5 Add resolver, download ownership, integrity, restart and deletion tests.

## 5. Transcriber and native-work coordination

- [ ] 5.1 Implement `WhisperCppAudioTranscriber` behind the existing `AudioTranscriber` contract.
- [ ] 5.2 Add a process-scoped coordinator that serializes Whisper and LLM native execution with cancellation-safe ownership.
- [ ] 5.3 Give direct-audio LLM generation priority and queue asynchronous transcript enrichment afterward.
- [ ] 5.4 Run required text-only transcription first and release active Whisper native resources before LLM generation.
- [ ] 5.5 Add concurrency, ordering, cancellation and stale-result tests for both routing modes.

## 6. Chat integration and migration

- [ ] 6.1 Wire Whisper availability from runtime plus validated ASR model state into Chat audio capability decisions.
- [ ] 6.2 Preserve compatible pending/completed/failed transcript persistence, context construction, titles and legacy audio decoding.
- [ ] 6.3 Present actionable Whisper setup when text-only voice input is unavailable while preserving direct audio for audio-capable models.
- [ ] 6.4 Persist and expose Whisper success/failure diagnostics without audio or transcript content.
- [ ] 6.5 Remove Android `SpeechRecognizer` from production wiring and eliminate speech-service/language-pack/pacing product behavior.

## 7. Acceptance and cleanup

- [ ] 7.1 Run unit tests, Spotless, Detekt, lint, debug builds, Android-test compilation, strict OpenSpec validation and `git diff --check`.
- [ ] 7.2 Validate complete pt-BR transcription, punctuation, cancellation, process restart and model deletion on the physical Samsung device.
- [ ] 7.3 Validate both native LLM runtimes and Whisper in one APK without symbol collision, crash or uncontrolled concurrent work.
- [ ] 7.4 Measure and document final APK delta, ASR model storage, peak RAM, latency and thermal observations.
- [ ] 7.5 Remove the superseded Android recognizer adapter/tests after the Whisper acceptance gate passes.
- [ ] 7.6 Update README, privacy/storage documentation and project OpenSpec context with the selected model and operational limits.
