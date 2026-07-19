# Change: Isolate resume state across fallback download URLs

## Why

All configured download URLs currently share one partial file. A fallback URL
can be asked to resume bytes written by a different source, and an integrity
failure then advances without retrying the healthy fallback from zero.

## What Changes

- Prevent partial bytes from one source from poisoning a different fallback.
- Retry a fallback once from byte zero when resumed content fails integrity.
- Keep retries bounded and preserve valid same-source resume behavior.
- Add tests for incompatible mirrors, ignored ranges, cancellation, and success.

## Impact

- Affected spec: `local-llm-hub`
- Affected code: model downloader, byte-source response metadata, downloader tests
- Catalog format and final atomic promotion: unchanged
