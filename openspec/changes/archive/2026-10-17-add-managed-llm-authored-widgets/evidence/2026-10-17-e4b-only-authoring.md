# E4B-only widget authoring — 2026-10-17

Product decision: E2B remains available for Chat but does not need a new
physical widget-authoring acceptance run. The managed/resumable widget-authoring
protocol is advertised only for E4B. Isolated, non-persistent characterization
probes remain separate from widget creation.

The fixed model catalog declares `widget_authoring_pipeline_v1` only for E4B.
The creation screen and notification Retry path no longer elevate an ineligible
model through the debug characterization helper. An unsupported model is
rejected before model load by the pipeline.

Validation: focused catalog/pipeline tests and the full `spotlessCheck detekt
testDebugUnitTest lintDebug assembleDebug` gate passed; strict OpenSpec
validation passed (10/10), and `git diff --check` passed. The debug APK was
installed with `adb install -r` on the connected SM-S901E. The local database
had 12 widgets, 12 revisions, 12 presentations and 255 runs both before and
after installation. No new model-dependent generation was attempted here.

Task 9.3 acceptance uses the agreed practical scope: physical E4B creation
in Portuguese (date-event widgets documented in `docs/device-validation.md`)
and English (the 2026-10-16 resumable-authoring evidence), physical negative
feasibility (`2026-10-16-sms901e-unachievable-feasibility.md`), user-reported
cancellation/resume, and observed controlled failures and repairs across the
October device retests. Parser, refusal, bounded-repair and unavailable-path
cases are covered by repository tests. The previous exhaustive physical matrix
was not run and is not claimed. Under JJ's reduced-risk acceptance decision,
task 9.3 is complete without a new E2B authoring test.

Task 12.4 uses the same practical acceptance decision together with the
completed physical lifecycle checks in task 9.4. Detailed stage timings and
total authoring latency were not collected after finalized sessions removed
their attempt history; those metrics are explicitly outside this release gate,
not represented as passing measurements. E4B alone advertises authoring in
the fixed catalog, and widget activation still requires a confirmed valid
draft.
