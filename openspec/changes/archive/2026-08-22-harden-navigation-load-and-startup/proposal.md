# Change: Harden navigation-time loading and startup

## Why

Repeatedly entering and leaving Voice Chat while native model preparation is in progress can start overlapping native loads after coroutine cancellation. Startup also performs model hashing and media reconciliation before the first frame, extending the splash screen.

## What changes

- Render the home wordmark on a black surface in every theme.
- Serialize native model loads, initialize the requested Voice Chat profile directly without an intermediate text-only engine, and make completion safe when callers leave a screen.
- Ignore stale Voice Chat load results after navigation.
- Reuse a model-integrity verification only while the app-owned artifact's expected digest, size, and modification metadata remain unchanged.
- Move chat-media reconciliation off the UI startup path.

## Validation

- Unit tests cover coalesced/serialized runtime loading, direct workload-profile initialization, stale Voice Chat completion, and integrity-cache invalidation.
- Run the repository quality gate.
- Physical-device validation remains required for startup timing, native memory pressure, and repeated Voice Chat navigation.
