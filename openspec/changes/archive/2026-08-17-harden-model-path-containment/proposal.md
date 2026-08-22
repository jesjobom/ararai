## Why

Model catalog paths are checked only for a `models/` prefix. A malformed bundled
catalog entry containing traversal or non-normalized segments could therefore
escape the application-owned model directory during resolution, download,
migration, or deletion. The catalog is reviewed and bundled, so this is defense
against configuration and supply-chain mistakes rather than a remote file-write
primitive.

## What Changes

- Reject absolute, traversal, empty, and non-normalized model path segments when
  parsing the catalog.
- Enforce canonical containment again at every model filesystem ownership
  boundary before reading, promoting, migrating, or deleting a file.
- Preserve valid nested paths below `models/` and existing download integrity
  checks.

## Capabilities

### Modified Capabilities

- `local-llm-hub`: Model files remain confined to the application-owned model
  directory even when catalog configuration is malformed.

## Impact

- Affected code: model catalog parser, resolver, downloader, migration/deletion
  boundaries, and focused tests.
- Dependencies: none.
