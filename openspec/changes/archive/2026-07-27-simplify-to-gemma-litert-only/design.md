## Context

ArarAI currently dispatches chat inference between a custom llama.cpp JNI stack
for GGUF files and LiteRT-LM for Gemma 4 bundles. The checked-in catalog has four
non-Gemma GGUF chat models, two Gemma 4 LiteRT-LM models, and two Whisper
transcription models. Model weights are downloaded after installation, but both
runtime implementations ship in the APK.

The change is cross-cutting because runtime metadata is used by catalog parsing,
model resolution, startup, benchmarks, UI labels, tests, native build
configuration, and documentation. Whisper is a separate utility runtime and
must remain intact.

## Goals / Non-Goals

**Goals:**

- Make Gemma 4 E2B/E4B through LiteRT-LM the complete supported chat catalog.
- Make E2B the default because it has the lower storage and memory requirement.
- Remove all production llama.cpp/GGUF code and native build dependencies.
- Preserve the runtime-neutral `LocalLlmEngine` boundary for UI and domain code.
- Preserve local Whisper transcription.
- Remove known legacy GGUF downloads safely during upgrade.
- Record an APK size comparison and retain physical-device validation as an
  explicit handoff.

**Non-Goals:**

- Improving Gemma token throughput or memory behavior.
- Removing Whisper, VAD, ONNX Runtime, or voice features.
- Supporting user-imported models or arbitrary LiteRT-LM bundles.
- Proving real-model, GPU, thermal, or device compatibility in the container.

## Decisions

### Keep the engine boundary but remove runtime dispatch

`LocalLlmEngine` remains the stable application boundary, while
`ConfiguredLocalLlmEngine` and `LlamaCppLocalLlmEngine` are removed. Application
wiring will directly use `LiteRtLmLocalLlmEngine`.

This avoids leaking LiteRT-LM API types through Chat and Voice code while
removing an abstraction whose only purpose was selecting between runtimes.
Keeping an inactive llama.cpp adapter was rejected because its native binary and
maintenance surface are the primary costs being removed.

### Narrow model metadata to supported formats

The catalog continues to declare runtime, artifact format, and acceleration so
Whisper and LiteRT-LM entries remain self-describing. LlamaCpp, GGUF,
GPU-layer-count parsing, and their compatibility defaults are removed.

Retaining dead enum values was rejected because it would falsely advertise a
supported runtime and preserve branches and tests that cannot execute.

### Treat Whisper independently

WhisperCpp and WhisperGgml remain valid utility metadata and continue through
the dedicated transcription runtime. “Gemma-only” applies to chat/reasoning
LLMs, not audio transcription.

### Delete only known legacy artifacts

At startup, a one-time, idempotent migration deletes the four former catalog
GGUF filenames and their partial-download files from the app-owned model
directory. It does not recursively delete unknown files. Exact-path deletion is
idempotent, so an interrupted migration can safely retry on the next startup.

Leaving multi-gigabyte files orphaned was rejected. Broad directory cleanup was
also rejected because it could remove current or future model artifacts.

### Measure release-equivalent artifacts

The comparison uses the same build variant before and after implementation,
reporting APK bytes and the removed native library contribution. The result
does not claim an inference-speed improvement; real-model behavior remains a
physical-device check.

## Risks / Trade-offs

- [Older or low-memory devices lose the 105 MB SmolLM2 fallback] → Document
  Gemma E2B's storage/RAM baseline and validate the supported device profile.
- [Upgrade cleanup could delete a user-renamed unrelated file] → Delete only
  exact former app-managed relative paths and their downloader partials.
- [The concurrent instructions/tools change mentions unsupported runtimes] →
  Keep this change isolated and reconcile its proposal/spec during application
  if it is implemented later.
- [APK size varies by build type and compression] → Compare identical variants
  and report raw measurements rather than estimates.
- [Build success cannot validate LiteRT-LM GPU behavior] → Retain the device
  validation checklist and explicitly report it as pending.

## Migration Plan

1. Ship the narrowed catalog with Gemma E2B as default.
2. On first startup, remove only former GGUF managed files and partials.
3. Existing selection resolution falls back to the configured default when the
   old selected ID no longer exists.
4. Remove the llama.cpp implementation and build integration in the same
   release so the APK cannot advertise or carry the removed runtime.
5. Rollback requires restoring the prior catalog/runtime build; deleted GGUF
   weights would need to be downloaded again.

## Open Questions

- Physical-device validation must establish whether E2B is acceptable on the
  minimum supported product device profile before production release.

## Implementation Evidence

- Baseline debug APK: 160,394,116 bytes.
- Gemma-only debug APK: 114,845,774 bytes.
- Reduction: 45,548,342 bytes (28.4%).
- The baseline packaged `libararai_llama.so` at 45,504,448 uncompressed bytes;
  the Gemma-only APK contains no `libararai_llama.so`.
- `scripts/quality-gate.sh` passed, including formatting, detekt, 239 unit and
  Robolectric tests, lint, debug APK builds, instrumentation compilation, and
  strict validation of all OpenSpec items.
- The APK was copied to
  `/home/node/.openclaw/jarvis/artifacts/ararai/app-debug.apk`.
- Physical-device Gemma E2B/E4B inference, multimodal input, cancellation,
  lifecycle, memory, GPU/backend, and thermal validation remain required.
