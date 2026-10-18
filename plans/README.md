# ArarAI Improve implementation plans

Reviewed 2026-10-17 against commit `38acb8b`. This is an advisory backlog, not
authorization to modify application code or deliver a release. The previous
2026-08-17 Improve register is preserved in
[`2026-08-17-audit-archive.md`](2026-08-17-audit-archive.md). Its six findings
were assigned completed, archived OpenSpec changes; do not reopen their old
file/line claims without new evidence. The current `ArarAiApp.kt` remains large,
but its earlier extraction change was completed within its stated scope.

The advisor read first-party source, tests, specs, quality-gate docs, and
recent device evidence. `scripts/check-java-runtime-alignment.sh` passed. The
survey was hotspot-weighted, not a line-by-line audit of the whole repository.
No build, test, static scanner, device action, or networked dependency audit was
run during this read-only review. CI and physical-device behavior are not
established by this audit.

## Recommended execution order

| Plan | Finding | Priority | Effort | Fix risk | Depends on | Status |
| --- | --- | --- | --- | --- | --- | --- |
| [001](001-preserve-active-authoring-service.md) | A stale notification action stops an active authoring foreground service | P1 | M | Medium | None | Archived; physical lifecycle gap recorded |
| [002](002-bound-model-download-size.md) | Model download writes beyond expected size before rejecting | P2 | S | Low | None | Archived |
| [003](003-serialize-litert-transitions.md) | LiteRT session transitions can close an active generation | P1 | M | Medium | None | Archived |
| [004](004-preserve-generation-stream-integrity.md) | Full channels silently discard model deltas or terminal events | P1 | M | Medium | 003 | Archived; physical matrix gap recorded |

Implementation and test evidence are in the archived OpenSpec change
[`2026-10-18-harden-model-runtime-and-authoring-reliability`](../openspec/changes/archive/2026-10-18-harden-model-runtime-and-authoring-reliability/).
JJ explicitly accepted archival with the remaining physical validation gaps,
which are listed in its `evidence/2026-10-18-closure.md`.

Plan 004 follows 003 because both change `LiteRtLmLocalLlmEngine.kt` and their
concurrency/cancellation behavior must be verified together. Plans 001 and 002
are independent. Each executor must read its full plan, verify its drift check,
and update only its own status row after validation; do not push, commit, or
archive an OpenSpec change unless separately authorized by the operator.

## Vetted opportunities not planned in this default set

- **Android lifecycle coverage (tests, high confidence):** The actual
  `ForegroundServiceDidNotStartInTimeException` is recorded at
  `docs/device-validation.md:3`; current
  `WidgetAuthoringServiceIntentTest.kt:46-105` uses Robolectric intent/disposition
  checks and `scripts/quality-gate.sh` assembles rather than executes Android
  instrumentation. Plan 001 includes a focused service-lifecycle test and a
  disposable-device execution gate. Do not run connected tests on the retained
  SM-S901E: repository `AGENTS.md` explicitly prohibits it.
- **Persistence architecture (medium leverage):**
  `ManagedWidgetRepository.kt:297-562` owns schema/migrations, `:665-999` owns
  execution state, and `:1001-1699` owns authoring state. A later
  characterization-first extraction could separate SQL delegates while keeping
  one transaction/`SQLiteOpenHelper` owner (L effort, medium fix risk). This is
  not a precondition for the four correctness fixes.
- **Product-direction spike, not a defect:**
  `WidgetCollectionCardArtifact.kt:14-54` permits one path per title/body,
  while `docs/managed-widgets.md:85-99` routes composed post-result text to
  model-written custom source. The physical render-selection evidence in
  `openspec/changes/archive/2026-10-15-simplify-widget-result-access/evidence/2026-10-15-sms901e-render-selection.md`
  records repeated rejected attempts. Investigate a *bounded typed* title/body
  composition contract only if real requests recur; retain provenance and the
  documented no-filter/no-multiple-result limits. Product value is plausible,
  not proven by this code audit (L effort, medium confidence).
- **Project-context drift (docs, high confidence):** `openspec/project.md:57`
  says no model advertises `widget_authoring_pipeline_v1`, but
  `app/src/main/res/raw/fixed_model.properties:44` and consolidated spec
  `openspec/specs/local-llm-hub/spec.md:4470` say E4B does. Correct during the
  next documentation/OpenSpec maintenance pass; the consolidated spec and
  catalog take precedence, so this is not an eligibility-code defect.

## Considered and rejected

- Reopening the August findings ARA-AUD-001 through ARA-AUD-005: current path
  containment, credential recovery, release shrinking, Java alignment, and HTTP
  ownership code contradict the archived observations; archived follow-up
  changes document their implementation.
- Treating `ArarAiApp.kt` size alone as a new P1: an incremental extraction was
  completed and no specific current behavioral defect follows solely from its
  line count. Review destination boundaries when touched next.
- Running connected instrumentation on a data-bearing personal phone: the
  repository warns the Gradle task may uninstall the package and erase app data.
