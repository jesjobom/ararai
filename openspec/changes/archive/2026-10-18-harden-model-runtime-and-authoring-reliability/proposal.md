# Proposal

## Why

The 2026-10-17 Improve review identified four independent reliability gaps at
boundaries shared by model downloads, local inference, and resumable widget
authoring. They can leave an active authoring attempt without foreground
protection, consume storage beyond a model's declared size, close a native
session during generation, or silently accept a truncated generation stream.

## What Changes

- Make widget-authoring notification actions revision-aware for both workflow
  mutation **and service lifetime**: a stale Cancel, Continue, or Retry action
  cannot stop foreground execution for a newer active attempt. Preserve the
  promotion-before-inspection rule for queued foreground starts.
- Bound each configured model download while bytes are copied, including
  resumed transfers. Reject a missing size before transfer and discard an
  oversized partial; preserve valid resume, fallback, checksum validation,
  and atomic promotion.
- Give active LiteRT-LM generation exclusive ownership of its native session
  against load, prepare, reload, recovery, and unload transitions on the shared
  application engine. Keep cancellation and resource release bounded.
- Deliver generation events without silently discarding them. A successful
  terminal event is permitted only after all prior events are delivered; if a
  non-suspending native callback cannot enqueue data within bounded capacity,
  generation fails explicitly and cannot retain an apparently complete but
  partial conversation.

No new model, provider, permission, remote service, or user-facing feature is
introduced. These are compatible corrections to existing contracts. Work on
the two LiteRT-LM gaps is ordered because both affect the same engine and its
cancellation behavior.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `llm-widget-authoring`: keep foreground service ownership tied to the latest
  durable authoring attempt when notification actions arrive late or twice.
- `local-llm-hub`: enforce configured model download size during transfer,
  serialize native session transitions with generation, and guarantee complete
  ordered delivery or explicit failure of model events.

## Impact

- Production code likely affected: `WidgetAuthoringService.kt`,
  `ModelFileDownloader.kt`, and `LiteRtLmLocalLlmEngine.kt`. Application-level
  model catalog, widget protocol, Chat/Voice persistence formats, and
  JavaScript sandbox authority stay unchanged.
- Tests: deterministic JVM/Robolectric cases for stale actions, byte limits,
  generation/transition interleavings, and slow consumers; a focused Android
  foreground-service lifecycle test on a disposable arm64 target. The normal
  gate assembles instrumentation but does not execute it.
- Validation: targeted tests, `scripts/quality-gate.sh`, strict OpenSpec
  validation, and explicit physical-device evidence for changed foreground
  service/native runtime behavior. Connected Android tests must not run on a
  retained installation because the task can uninstall the app and erase data.
- No new dependency is planned. This change is the consolidated OpenSpec
  authority for the four reviewed behavioral gaps and remains usable without
  the separate Improve advisory files.
