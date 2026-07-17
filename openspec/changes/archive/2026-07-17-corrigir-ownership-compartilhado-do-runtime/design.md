# Design: Application-scoped local runtime owner

## Decision

`AppLocalLlmRuntime` owns exactly one `ConfiguredLocalLlmEngine` for the life of
the application composition. Both Chat and Benchmark receive that same engine.

The existing navigation contract prevents both screens from starting work at
the same time. Leaving Chat cancels an active generation before Diagnostics is
shown. Benchmark always unloads in `finally`, releasing retained native and GPU
resources. Chat already calls `load` for every submission, so it safely reloads
after a benchmark.

## Why not a runtime pool

A pool would preserve the failure mode by allowing multiple native sessions.
The product currently has one foreground inference consumer, so a single owner
is the appropriate constraint.

## Why not add locking yet

The app navigation and ViewModel guards already serialize user-started Chat and
Benchmark work. This change fixes the proven duplicate ownership root cause.
If future background inference introduces concurrent consumers, the owner can
grow a lease/mutex API with an explicit busy policy.

## Lifecycle

- Chat may retain the loaded model across internal navigation.
- Starting Benchmark reuses the same configured engine tree.
- Benchmark completion/cancellation unloads the shared runtime.
- A later Chat submission reloads the selected model.

