## 1. Specification

- [x] 1.1 Define required Kotlin quality checks, reproducible caches, and canonical Purpose.

## 2. Tooling

- [x] 2.1 Add pinned formatting configuration and check tasks.
- [x] 2.2 Add pinned Kotlin static analysis with a reviewed baseline.
- [x] 2.3 Add both checks to the shared quality gate and documentation.
- [x] 2.4 Cache compatible Android/native tooling and FetchContent inputs in CI.

## 3. Documentation

- [x] 3.1 Replace the canonical specification's placeholder Purpose.
- [x] 3.2 Document cache invalidation and local quality commands.

## 4. Validation

- [x] 4.1 Verify formatter/analyzer failure and success paths.
- [ ] 4.2 Verify cold-cache, warm-cache, and invalidated-cache CI runs.
- [x] 4.3 Run the project quality gate and strict OpenSpec validation.
