## 1. Characterize ownership

- [x] 1.1 Add a controllable connection adapter and failing tests for cleanup on success, non-2xx response, I/O failure, cancellation, and URL fallback.
- [x] 1.2 Verify existing streaming, progress, integrity, and atomic-promotion behavior before changing ownership.

## 2. Make cleanup deterministic

- [x] 2.1 Introduce the smallest closeable response/cleanup boundary that retains the connection handle.
- [x] 2.2 Close response streams and disconnect connections exactly once on every terminal path.
- [x] 2.3 Preserve cancellation propagation and fallback semantics.

## 3. Validate

- [x] 3.1 Run focused model downloader and cancellation tests.
- [x] 3.2 Run the complete quality gate and strict OpenSpec validation.
