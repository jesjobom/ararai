# Change: Reconcile orphaned Chat media beyond the scan limit

## Why

Startup reconciliation limits the first sorted files before excluding referenced
media. When those first files remain referenced, orphaned files later in the
directory can be skipped on every launch and accumulate indefinitely.

## What Changes

- Apply the reconciliation limit to deletion candidates rather than all files.
- Preserve canonical-path ownership and persisted-reference safeguards.
- Add boundary tests with more than 256 referenced files followed by orphans.
- Keep startup work bounded and deterministic.

## Impact

- Affected spec: `local-llm-hub`
- Affected code: Chat media repository and tests
- Persisted messages and valid referenced media: unchanged
