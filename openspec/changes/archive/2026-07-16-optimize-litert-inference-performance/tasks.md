## 1. Runtime Metrics Contract

- [x] 1.1 Add failing tests for generation results with and without native
  prefill/decode performance metrics.
- [x] 1.2 Extend the engine event/observability boundary with optional structured
  TTFT, prefill-token, and decode-token metrics without treating text deltas as
  tokenizer tokens.
- [x] 1.3 Map LiteRT-LM `Conversation.getBenchmarkInfo()` into the runtime-neutral
  metrics contract after successful generation.

## 2. Accurate Benchmark Reporting

- [x] 2.1 Add failing BenchmarkViewModel tests for native metrics and the
  honest no-token-metrics fallback.
- [x] 2.2 Replace callback-count throughput with separate native prefill and
  decode results, retaining load and end-to-end elapsed time as distinct values.
- [x] 2.3 Update benchmark UI labels and values so every displayed unit matches
  the underlying measurement.

## 3. Workload-Aware LiteRT-LM Profiles

- [x] 3.1 Add failing bridge/engine tests for text-only initialization,
  image/audio profile activation, unsupported modalities, and profile switching.
- [x] 3.2 Introduce a LiteRT-LM workload profile that defaults to the language
  backend only and initializes vision/audio backends only when requested.
- [x] 3.3 Recreate the engine and invalidate its conversation safely when the
  next supported request requires a different profile.

## 4. LiteRT-LM Cache

- [x] 4.1 Add tests for dedicated app-cache injection and uncached fallback when
  the cache directory is unavailable.
- [x] 4.2 Create and inject a dedicated app-owned LiteRT-LM cache directory while
  keeping cache setup failure non-fatal and observable.

## 5. Safe Conversation Reuse

- [x] 5.1 Add chat-session identity and transcript compatibility metadata to the
  runtime request boundary, with focused request-construction tests.
- [x] 5.2 Add failing LiteRT-LM tests for compatible continuation, session or
  transcript mismatch, setting/profile changes, benchmark isolation,
  cancellation, errors, and unload.
- [x] 5.3 Initialize fresh LiteRT-LM conversations from structured system/history
  state and retain a canonical transcript after successful completion.
- [x] 5.4 Reuse the retained conversation for compatible next turns by sending
  only new content, and conservatively close it on every invalidation condition.

## 6. Validation

- [x] 6.1 Run targeted benchmark, ChatViewModel, and LiteRT-LM engine JVM tests.
- [x] 6.2 Run the complete debug unit-test suite and assemble debug and release
  APKs.
- [x] 6.3 Run `openspec validate optimize-litert-inference-performance --strict`.
- [x] 6.4 Copy the validated debug APK to the external artifact handoff path.
