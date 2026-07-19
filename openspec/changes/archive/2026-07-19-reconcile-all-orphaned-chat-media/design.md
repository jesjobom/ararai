# Design: Fair bounded media reconciliation

## Context

`FileChatMediaRepository.reconcile` sorts app-owned files, takes the configured
limit, and only then removes referenced files. Repeating the same prefix on each
startup can starve later orphan candidates forever.

## Decisions

Canonicalize and filter referenced files before applying the maximum deletion
candidate count. The limit bounds deletion work, while directory enumeration
remains required to identify candidates. Keep deterministic ordering for tests.

If directory sizes make enumeration itself a measured startup problem, introduce
a persistent cursor in a later change rather than adding complexity without data.

## Validation

- Unit tests cover a referenced prefix larger than the limit, mixed candidates,
  external paths, content URIs, zero limits, and deletion failures.
- Startup behavior remains side-effect-safe and the project quality gate passes.
