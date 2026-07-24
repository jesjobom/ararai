# whisper.cpp Android spike

## Question

Can ArarAI load whisper.cpp beside its existing native LLM runtimes and
transcribe representative pt-BR recordings with acceptable quality, latency,
memory use and cancellation behavior on Samsung SM-S942W?

## Pinned upstream

- Repository: https://github.com/ggml-org/whisper.cpp
- Release: `v1.9.1`
- Commit: `f049fff95a089aa9969deb009cdd4892b3e74916`
- License: MIT (`LICENSE` SHA-256
  `94f29bbed6a22c35b992c5c6ebf0e7c92f13b836b90f36f461c9cf2f0f1d010d`)
- Release date: 2026-06-19

The production integration must pin the commit rather than a moving branch.
The model weights have their own provenance and integrity metadata and are not
covered by the runtime license record above.

## Equal-input comparison

The physical runner will compare multilingual quantized `tiny`, `base` and `small`
using the same 5-10 app-recorded 16 kHz mono PCM WAV files. For each candidate
it must record:

- artifact bytes and SHA-256;
- APK native-size delta;
- model load time;
- full transcription time and real-time factor;
- peak process PSS/RSS;
- final pt-BR transcript and human quality notes;
- CPU thread counts 2, 4, 6 and 8 using the same saved audio;
- cancellation latency and post-cancellation memory;
- ability to load and invoke the existing llama.cpp and LiteRT-LM paths in the
  same APK without symbol collision or crash.

At least one long recording and one cancellation during decode are required as
stress cases. Audio and transcript content stay on the test device; only
sanitized measurements belong in the verdict.

## Verdict: PARTIAL

Question: Is the selected whisper.cpp source credible and pin-able for an
isolated Android prototype?

Evidence: upstream `v1.9.1` resolves to the pinned commit above, has a current
2026 release and uses the MIT license.

What worked: the runtime source/provenance gate and the app-side unified model
catalog foundation are ready. The isolated `whisper-runtime` arm64 module and
the complete debug APK build successfully. The APK contains both
`libararai_llama.so` and `libararai_whisper.so`; the stripped Whisper library
exports its JNI wrapper without exporting `ggml_*` or `whisper_*` C symbols.
The first device result for Base Q5_1 produced RTF 3.532 on a 14.784-second
pt-BR sample and exposed that the Android debug native build had no
optimizer flag and no ARM dot-product/FP16 target. The benchmark runtime now
forces `-O3` plus `armv8.2-a+dotprod+fp16`, while leaving the app itself
debuggable. It exposes 2/4/6/8-thread runs for the same saved WAV. The optimized
stripped Whisper library is 2,733,776 bytes, and the complete debug APK is
160,328,276 bytes before model weights.

What remains: on-device simultaneous library loading, candidate model
measurements and coexistence testing require the physical Samsung device. No
default Whisper model may be
added to the production catalog until that comparison records its URL, bytes
and SHA-256 and selects a winner.

Recommendation: continue with the isolated runtime prototype, then promote
only the selected artifact metadata into the production catalog.
