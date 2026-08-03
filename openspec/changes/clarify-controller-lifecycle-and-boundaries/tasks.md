## 1. Specification

- [x] 1.1 Define lifecycle ownership and incremental modularization requirements.

## 2. Chat lifecycle

- [x] 2.1 Add an idempotent close contract backed by an owned child job.
- [x] 2.2 Prevent work after disposal and test cancellation plus parent-scope isolation.

## 3. Composition root

- [x] 3.1 Extract shared runtime and controller construction from `ArarAiApp`.
- [x] 3.2 Dispose controllers and runtime when their Compose owner leaves composition.

## 4. LiteRT modularization

- [x] 4.1 Extract retained-resource ownership from the Android LiteRT adapter.
- [x] 4.2 Extract pure conversation-reuse policy without changing public contracts.

## 5. Validation

- [x] 5.1 Run focused lifecycle and LiteRT tests.
- [x] 5.2 Run the complete project quality gate and strict OpenSpec validation.
- [x] 5.3 Record remaining instrumentation and physical-device boundaries.
