# Tasks

## 1. Preserve foreground ownership for current widget authoring

- [x] 1.1 Replace unconditional service stop/removal in stale resumable
  Cancel/Continue/Retry branches with a latest-snapshot, start-ID-aware
  disposition; keep the existing promotion-before-inspection refresh contract
  and legacy authoring behavior. Verify
  `./gradlew :app:testDebugUnitTest --tests 'com.jesjobom.ararai.WidgetAuthoringServiceIntentTest'`
  exits 0.
- [x] 1.2 Extend `WidgetAuthoringServiceIntentTest` with stale Cancel,
  Continue, and Retry against a newer running revision, matching Cancel,
  duplicate delivery, and a newer service start during asynchronous action
  completion. Verify the targeted JVM test above passes and an ongoing attempt
  retains its notification/cancel control.
- [ ] 1.3 Add a focused Android service-lifecycle instrumentation test for
  rapid queued-to-terminal and stale-action-to-new-running transitions;
  document the disposable-target command and retained-device prohibition in
  `docs/device-validation.md`. Verify `./gradlew :app:assembleDebugAndroidTest`
  exits 0, then execute the focused `connectedDebugAndroidTest` class **only**
  on a disposable arm64 device with
  `-Pararai.allowDestructiveConnectedTests=true`; file the device outcome in
  this change's `evidence/`. Leave this task unchecked if device execution is
  unavailable rather than treating APK assembly as a pass.

## 2. Bound configured model transfers while writing

- [x] 2.1 Reject missing/non-positive `expectedBytes` before opening the byte
  source and discard an already oversized `.part` before a resume request.
  Add `ModelFileDownloaderTest` cases asserting the source is not opened for a
  missing bound and an oversized partial is not resumed. Verify
  `./gradlew :app:testDebugUnitTest --tests 'com.jesjobom.ararai.model.ModelFileDownloaderTest'`
  exits 0.
- [x] 2.2 Enforce the remaining-byte allowance before every disk write,
  close the response on overflow, remove the excess partial, and preserve an
  existing valid final file. Add exact-size, one-byte-over, and resumed-over
  tests. Verify the targeted downloader test command exits 0 and no test
  leaves an oversized `.part` or promotes excess bytes.
- [x] 2.3 Characterize fallback isolation, cancellation, and valid HTTP-range
  resume after the size guard, using the existing downloader fake byte sources.
  Verify all `ModelFileDownloaderTest` cases pass and no catalog URL, digest,
  or `ModelFileIntegrity` checksum behavior changed.

## 3. Serialize native session ownership and generation

- [x] 3.1 Add a deterministic paused-generation fake in
  `LiteRtLmGenerationSerializationTest` for unload, reload, and differing
  workload transitions; make those transitions acquire the generation lease
  before the transition gate without changing the shared-engine owner. Verify
  `./gradlew :app:testDebugUnitTest --tests 'com.jesjobom.ararai.engine.LiteRtLmGenerationSerializationTest' --tests 'com.jesjobom.ararai.engine.LiteRtLmReloadTest'`
  exits 0 and no fake session closes while its generation is active.
- [x] 3.2 Cover cancelled collection, recovery waiting, and native
  load-publication cleanup with the same fake bridge; ensure a waiting
  transition proceeds after cancellation without deadlock or orphaned session.
  Verify the two targeted engine test classes above pass under
  `StandardTestDispatcher` and document the lock order near the engine gates.

## 4. Preserve generation event integrity after task group 3

- [x] 4.1 Confirm the native callback's cancellation/threading contract before
  implementing overflow cleanup; record the safe dispatch decision in
  `design.md` and add a testable bounded callback-delivery seam. Verify a
  focused fake-callback JVM test compiles and runs without a production model;
  stop and report if cancellation cannot be dispatched safely.
- [x] 4.2 Replace ignored sends in the suspending outer engine coroutine with
  ordered, cancellation-aware delivery. Add a fake bridge that emits hundreds
  of chunks while its collector is slowed; verify
  `./gradlew :app:testDebugUnitTest --tests 'com.jesjobom.ararai.engine.LiteRtLmLocalLlmEngineTest' --tests 'com.jesjobom.ararai.engine.LiteRtLmGenerationSerializationTest'`
  exits 0 and concatenated deltas precede exactly one Completed event.
- [x] 4.3 Make the non-suspending native callback check every result-affecting
  event enqueue. On bounded overflow, mark the round failed atomically,
  arrange safe native cleanup, prevent a later `onDone` from retaining a
  partial conversation, and emit no success event. Verify a deterministic
  held-consumer fake-callback test reports controlled failure, never
  Completed, with no retained partial transcript.
- [x] 4.4 Add normal caller-cancellation and tool-state ordering regressions
  and confirm overflow telemetry/failure messages contain no prompt, token,
  tool argument, or raw native exception. Verify the focused engine tests pass
  and `WidgetAuthoringPipelineModelTest` still distinguishes Completed/Failed
  from absent artifacts.
- [ ] 4.5 On a supported arm64 target, perform synthetic Chat/Voice and widget
  generation smoke checks for cancellation, repeated generation, and model
  reload, then file build/model/device identifiers and outcomes under this
  change's `evidence/` without private content. Verify no successful run has a
  truncated response; leave this task unchecked if physical execution is not
  available.

## 5. Integration validation

- [x] 5.1 Run `scripts/quality-gate.sh`, `openspec validate --all --strict`,
  and `git diff --check`; verify all exit 0 without adding new Detekt-baseline
  suppressions or changing unrelated files.
- [ ] 5.2 Review the diff against all four requirements in this change's two
  spec deltas and decisions D1-D4 in `design.md`; verify each behavior has
  passing targeted tests, no in-scope work was skipped, and device checks are
  marked passed only where physical evidence was actually recorded.

## Reassessment — 2026-10-18

Twelve of fifteen tasks are complete; the three unchecked tasks above remain
open. The E4B image low-memory failure also occurs on pre-widget `main`, so it
is a separately documented device/workload limitation, not evidence that this
change introduced an image regression or a reason to mark its checks passed.

- **1.2 complete:** Tests cover stale Cancel/Continue/Retry, matching Cancel,
  stable duplicate action identity, an older asynchronous completion after a
  newer start ID, and an ongoing notification with its Cancel action. The
  controller's expected-attempt and duplicate-submission tests cover workflow
  mutation; physical service lifecycle remains task 1.3.
- **1.3 open:** A focused Android service-lifecycle instrumentation test and
  physical execution are still required. JJ explicitly authorized destructive
  testing on the connected SM-S901E on 2026-10-18; any use must be recorded
  separately from the JVM tests and may affect its retained app data.
- **3.1–3.2 complete:** Paused-generation fakes now cover unload, reload,
  different-workload preparation, cancellation, recovery waiting, and the
  next generation after reload. A separate paused native-load fake covers
  session publication after cancellation; profile switching now explicitly
  protects native load publication with `NonCancellable`.
- **4.3–4.4 complete:** The bounded callback gate rejects late completion
  after overflow; engine tests cover controlled failure without `Completed`,
  caller cancellation, and ordered tool/text events with a slow collector.
  Widget authoring now rejects a captured artifact if generation reports
  `Failed` or ends without `Completed`; overflow uses a fixed failure message
  and telemetry records only controlled event/outcome fields.
- **4.5 open:** Non-destructive Chat and widget checks, JJ's two successful
  audio-via-Chat tests (with and without transcription), and a physical E2B
  generation/cancellation/reload smoke are recorded. The requested dedicated
  Voice Chat and widget cancellation/reload matrix is incomplete. The separate
  Chat image A/B findings do not substitute for it.
- **5.2 open:** The final requirement-by-requirement acceptance review cannot
  pass while physical checks 1.3 and 4.5 remain open.

Host gate evidence is in `evidence/2026-10-18-host-validation.md`; physical
results and device restoration are in the dated files under `evidence/`.
JJ explicitly requested archival, commit, and push on 2026-10-18 despite
the three unchecked validation tasks. The accepted gaps and latest E2B
physical smoke are recorded in `evidence/2026-10-18-closure.md`.
