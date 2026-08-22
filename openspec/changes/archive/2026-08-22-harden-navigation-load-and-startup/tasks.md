## 1. Implementation

- [x] 1.1 Use a black home brand surface.
- [x] 1.2 Serialize native runtime load/unload transitions and prevent leaked sessions on cancellation.
- [x] 1.3 Invalidate stale Voice Chat loading attempts across navigation.
- [x] 1.4 Cache successful model integrity verification with safe metadata invalidation.
- [x] 1.5 Dispatch startup media reconciliation away from the main thread.
- [x] 1.6 Load the Voice Chat audio profile directly without constructing an intermediate text-only native engine.

## 2. Validation

- [x] 2.1 Add targeted regression tests.
- [x] 2.2 Run targeted tests.
- [x] 2.3 Run `scripts/quality-gate.sh`.
- [x] 2.4 Record required physical-device checks.
