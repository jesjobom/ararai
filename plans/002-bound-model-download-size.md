# Plan 002: Reject model downloads before writing beyond the declared size

> **Executor:** Read this plan, then run
> `git diff --stat 38acb8b..HEAD -- app/src/main/java/com/jesjobom/ararai/model/ModelFileDownloader.kt app/src/test/java/com/jesjobom/ararai/model/ModelFileDownloaderTest.kt openspec/changes`.
> Compare changed live code with the excerpts below; stop on mismatch. Do not
> commit, push, or archive without separate authorization.

## Status

- Priority P2; effort S; fix risk LOW; category security/reliability.
- Depends on: none. Planned at `38acb8b`, 2026-10-17.

## Why this matters

The app downloads multi-gigabyte model artifacts from catalogued HTTPS URLs.
Every checked-in artifact declares `expectedBytes`, but the copy loop writes
until EOF and size validation happens only afterwards. An unexpectedly large
response can consume available app/device storage before its integrity check
rejects it. The limit must be enforced while streaming, including resumed
partials, without weakening checksum validation or fallback behavior.

## Current state and conventions

- `ModelFileDownloader.kt:159-174` opens a byte response, chooses append vs
  restart, and calls `copyToWithProgress` with `totalBytes =
  config.expectedBytes` as progress metadata only.
- `ModelFileDownloader.kt:222-238` reads, writes, and adds `read` to
  `bytesCopied` without a bound. `ModelFileIntegrity.kt:27-30` checks expected
  size **after** the copy.
- `ModelFileDownloader.kt:176-195` rejects an invalid partial and promotes a
  verified artifact atomically. `:206-218` handles fallback. Preserve this
  ownership and SHA-256 verification; do not invent a network endpoint.
- `fixed_model.properties:23,49,71,89` supplies positive expected sizes for
  all four catalog entries. `ModelFileDownloaderTest.kt:119-157,269-333` has
  temp-file, resume, fallback, and retry fixture patterns to extend.
- The product is local-first and uses a checked-in catalog, not arbitrary user
  model URLs (`openspec/project.md`, purpose and current product).

## Commands

Run Gradle with JDK 17; `scripts/quality-gate.sh` additionally requires a
JDK 21 installation at `FIREBASE_JAVA_HOME` for Firebase emulator tests. See
`docs/quality-gates.md` for the pinned toolchain.

| Purpose | Command | Expected |
| --- | --- | --- |
| Focused unit tests | `./gradlew :app:testDebugUnitTest --tests 'com.jesjobom.ararai.model.ModelFileDownloaderTest'` | exit 0 |
| Full local/CI gate | `scripts/quality-gate.sh` | exit 0 |
| Spec validation | `openspec validate --all --strict` | exit 0 |

## Scope

In scope: `app/src/main/java/com/jesjobom/ararai/model/ModelFileDownloader.kt`,
`app/src/test/java/com/jesjobom/ararai/model/ModelFileDownloaderTest.kt`, and a
new active `openspec/changes/bound-model-download-size/` change. Do not alter
catalog URLs/digests, `ModelFileIntegrity.kt`, storage paths, the public progress
model, or unrelated downloader/service UI. Use existing Kotlin exception and
temporary-file patterns. A conventional `fix: ...` commit is appropriate only
if separately authorized.

## Steps

1. Write an OpenSpec delta: a configured download with declared size never
   writes more than that size; missing size fails before opening a network
   response; an oversized partial is discarded; valid resume/fallback and exact
   SHA validation remain. Verify `openspec validate --all --strict` exits 0.
2. At download start, require a positive `expectedBytes` for a downloadable
   model and clean any pre-existing partial whose length exceeds it. Before
   **each write**, compare `read` with remaining allowed bytes using
   subtraction (`read > limit - bytesCopied`) so addition cannot overflow.
   On overflow, close the response/output through existing `use` blocks,
   delete the partial, and raise a controlled `ModelDownloadException` that
   cannot leave an oversized resumable file. The valid exact-boundary response
   still proceeds to SHA-256 validation. Verify focused unit tests.
3. Add in-memory byte-source tests for exact size, one byte over, pre-existing
   oversized partial, resumed stream crossing the limit, and absent
   `expectedBytes`. Assert that no final artifact is promoted, no oversized
   `.part` remains, fallback ownership is preserved, and the source is not
   opened for a missing bound. Follow the existing resume fixture style.
   Verify focused unit tests, then the full quality gate.

## Test plan

- `ModelFileDownloaderTest` covers all five cases above and remains green with
  its existing cancellation, resume, fallback, and cleanup cases.

## Done criteria

- A stream larger than `expectedBytes` cannot increase `.part` past that size;
  malformed existing oversized partials are not resumed.
- `scripts/quality-gate.sh` and OpenSpec strict validation exit 0; only in-scope
  files are modified. Report tests not run instead of claiming a device check.

## STOP conditions

Stop if production catalog entries lack `expectedBytes`, if a supported model
source intentionally has an unknown length, or if preserving resume semantics
requires changing the public model configuration contract.

## Maintenance notes

Future catalog entries must declare an exact size; checksum validation remains
mandatory even when the length matches.
