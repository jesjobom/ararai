# Design: Explicit ownership with incremental extraction

## Controller lifecycle

`ChatViewModel` will own a child `SupervisorJob`, even when a caller supplies a
scope for dispatcher/test control. `close()` cancels that child and active
generation/presentation/persistence work without cancelling the caller's scope.
Calls after disposal must not start new work. Disposal is idempotent.

The Compose application owner will use `DisposableEffect` to close Chat and Voice
controllers and the shared local-LLM runtime. Navigation callbacks remain useful
for screen-specific stop/unload behavior, but composition disposal becomes the
final safety boundary.

## Composition root

Controller creation moves into a focused `rememberArarAiAppControllers` function.
The returned holder exposes the shared runtime and screen controllers needed by
the application shell. `ConversationSelection`, `ConversationCoordinator`, and
the engine remain single shared instances, preserving normal/voice session
continuity. Screen rendering and navigation remain in `ArarAiApp` in this slice.

## LiteRT boundaries

Generic retained-resource ownership and pure conversation reuse decisions move to
separate source files. The production Android session keeps SDK-specific engine,
conversation, tool, and callback integration. This is a code-boundary change only;
the public engine and bridge contracts remain unchanged.

## Validation boundaries

Unit tests prove cancellation, idempotent disposal, parent-scope isolation,
resource ownership, and reuse decisions. Compose navigation/recreation and real
LiteRT unload behavior remain physical-device/instrumentation validation items and
must not be inferred from JVM tests or APK assembly.

