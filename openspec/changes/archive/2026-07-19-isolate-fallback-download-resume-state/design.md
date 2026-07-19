# Design: Source-safe fallback resume

## Context

The downloader iterates a primary URL and fallbacks while using one `.part`
file. HTTP range confirmation proves only that a server accepted an offset; it
does not prove that its artifact prefix matches bytes obtained from another URL.

## Decisions

Track which source owns resumable partial bytes, or conservatively restart when
changing sources. If a resumed transfer reaches integrity validation and fails,
delete the partial and retry the same URL once from zero before advancing.

Bound the clean retry to one per URL and retain cancellation semantics. Final
availability still requires configured byte-size/hash validation followed by
atomic promotion.

## Validation

- Unit tests simulate primary failure followed by a fallback with a different
  prefix, a same-source resume, a server ignoring Range, and bounded failure.
- The existing download/integrity suite and project quality gate pass.
- Physical validation exercises configured hosts and cancellation/resume.
