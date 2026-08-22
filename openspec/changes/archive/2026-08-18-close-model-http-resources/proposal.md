## Why

Model downloads create `HttpURLConnection` instances without an explicit
disconnect contract. Successful input-stream closure usually releases resources,
but non-success responses can throw before a stream is owned, and fallback URLs
can repeat that lifecycle. Cleanup should be deterministic on every outcome.

## What Changes

- Make the model byte response explicitly closeable or retain an equivalent
  cleanup callback that owns both stream and connection.
- Guarantee cleanup for success, HTTP failure, I/O failure, cancellation, and
  fallback attempts.
- Preserve streaming, progress, checksum, and atomic promotion behavior.

## Capabilities

### Modified Capabilities

- `local-llm-hub`: Model download network resources are released deterministically on every terminal path.

## Impact

- Affected code: model byte source/downloader and connection-adapter tests.
- Dependencies: none.
