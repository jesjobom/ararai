## 1. Specification

- [x] 1.1 Define source-safe resume and bounded clean fallback retry behavior.

## 2. Implementation

- [x] 2.1 Associate partial download state with its source or restart on source change.
- [x] 2.2 Add one bounded zero-offset retry after incompatible resumed content.

## 3. Validation

- [x] 3.1 Add downloader regression tests for mirror and retry boundaries.
- [x] 3.2 Run the project quality gate and strict OpenSpec validation.
- [ ] 3.3 Validate cancellation and fallback resume on a physical device.
