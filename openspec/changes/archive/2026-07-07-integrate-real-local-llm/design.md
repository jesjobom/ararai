# Design Notes

## Runtime Boundary

The existing `LocalLlmEngine` interface remains the application boundary. The
chat UI and `ChatViewModel` should not depend directly on JNI, llama.cpp types,
native handles, or native lifecycle details.

The real implementation should have a small Kotlin facade that:

- loads the native library once;
- validates that the model path points to the configured available GGUF file;
- creates and owns the native context/handle;
- maps native load, token, completion, cancellation, and error results into
  `GenerationEvent`;
- releases the native context on `unload()`.

The fake engine should remain available for JVM tests and deterministic behavior
checks.

## Model Source

This change assumes the configured model is already present and valid at the
app-owned path resolved by `ModelStartupController`.

If the model is missing, invalid, or still downloading, the existing model
startup flow remains responsible for that state and the chat send action stays
disabled. This change must not add a picker or alternate file path.

## Loading And Lifecycle

The real engine may load lazily on first prompt or when entering chat, but the
user-visible behavior must be clear:

- sending is disabled while the model is unavailable, loading, or generating;
- load failures appear in chat state and do not crash the app;
- only one generation request runs at a time;
- leaving chat cancels active generation and releases native resources.

Repeated prompts in the same chat session may reuse a loaded native context if
the configured model and inference config did not change. If the model changes
or becomes unavailable, the engine should unload the old context before loading
again.

## Threading

Native model load and generation must not run on the main thread. Token events
should be delivered back through coroutine/flow state updates without blocking
Compose rendering.

Cancellation should be cooperative where possible. At minimum, the Kotlin layer
must stop collecting events, release native resources on unload, and prevent
additional UI updates from a canceled request.

## Validation

Most native behavior cannot be proven by JVM tests in the OpenClaw container.
The implementation should still add automated tests around:

- engine selection/wiring at the Kotlin boundary;
- chat state transitions for loading, generation, failure, and cancellation;
- preservation of existing fake-engine behavior.

Native runtime correctness requires a physical-device smoke test with a model
already present at the configured location. The expected manual validation is:

1. install the debug APK on the device;
2. ensure the configured GGUF file exists and passes current model resolution;
3. open chat;
4. submit a short prompt;
5. confirm streamed local output appears;
6. leave chat and confirm no crash or leaked active generation in logs.
