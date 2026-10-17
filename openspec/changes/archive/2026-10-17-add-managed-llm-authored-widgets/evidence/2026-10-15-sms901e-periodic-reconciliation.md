# SM-S901E periodic reconciliation and retained-widget checks — 2026-10-15

- Device: Samsung SM-S901E, America/Toronto. All checks kept Wi-Fi/ADB online.
- Before fix: debug `202610142346`, installed 2026-10-14 23:48:42 local.
  A forced JobScheduler start with a cold app process at 09:01:21 local was
  stopped while startup reconciliation updated the same periodic work from
  generation 29 to 30. WorkManager re-enqueued it without invoking the worker.
  The filtered log is `2026-10-15-sms901e-periodic-cold-start-before.log`,
  SHA-256 `177b0d172803502f1b043a312adad3267c8becadf99345790e2494c53ee5c3ba`.
- Reproduction test: two identical scheduler reconciliations advanced a work
  generation from 0 to 1 before the fix. The test now passes unchanged, while
  interval, revision and network-requirement changes still advance generation.
- After fix: debug `202610150911`, APK SHA-256
  `34d911f9b55d96e1ad7b1d425113ccee2e739931eeed3be8a45a5c3655bb7d36`,
  installed with data preservation at 09:18:21 local. After the one-time
  schedule-tag migration, an app-process kill and relaunch kept the selected
  work at generation 32 with the same enqueue timestamp. All eight existing
  periodic work definitions remained scheduled.
- A manual refresh of an existing hourly Wikipedia widget succeeded at
  08:58:53 and updated its persisted presentation. Its canonical
  `pt.wikipedia.org` link opened in Chrome only after an explicit tap. Killing
  and relaunching the app restored the same widget detail and cached card.
- A disabled duplicate of that confirmed widget was created solely for a
  reversible lifecycle check. Before enable, refresh produced no run. Enable
  registered one hourly work definition and WorkManager completed one run;
  manual refresh completed a second run. Disable cancelled work, blocked
  refresh and retained the cached card. Deletion removed the duplicate's
  definition, revision, presentation, runs and observations; the original
  eleven definitions and eight periodic jobs remained. The filtered worker log
  is `2026-10-15-sms901e-duplicate-worker.log`, SHA-256
  `7ff9a93d8042bf478e5f1b736c8e540395c74b0fdce3120fb34743a2c4995c1d`.
- A second forced JobScheduler invocation before the hourly interval was due was
  deferred by WorkManager's own timing guard. It is **not** evidence of an
  unattended periodic run. The natural due-time observation, network-loss
  recovery are tracked separately. The worker's application-provider path
  calls `ManagedWidgetExecutionCoordinator` and `QuickJsWidgetJavaScriptEngine`,
  not the lazy local-LLM runtime. OpenSpec 9.4 remains open.
- With ArarAI outside the foreground, Android dispatched five hourly workers
  naturally at 09:45:52–53. All five recorded `ControlledFailure/ToolUnavailable`
  and requested WorkManager retry. The OS reported `APP_BACKGROUND` network
  blocking for UID 10492, with DNS requests marked `isBlocked=true`, before it
  reported the UID network unblocked. The 09:50:53 retries failed the same way.
  At 15:06, four JobScheduler entries remained pending with `CONNECTIVITY`
  unsatisfied although Wi-Fi/ADB were online; one enqueued WorkSpec lacked a
  system job. This is a provider-access failure caused by background UID network
  policy, not a Wikipedia rate limit. Filtered log:
  `2026-10-15-sms901e-autonomous-network-block.log`, SHA-256
  `1af9078cbc0466f67fa52187bf68e7a1206337f99d91fb9b431de3e112b223d3`.
- Follow-up changes schedule network-backed work without the JobScheduler
  connectivity constraint and require a bounded UID-network readiness check in
  the worker before provider dispatch. Physical autonomous success remains to
  be verified; this entry does not close 9.4.
- The follow-up debug APK `202610151514` (SHA-256
  `f2c3cb16ef1bd4585f9e51789d81cd53d380b4b7ba249f1113e9b167ecb21eed`)
  installed at 15:21:38 with data preservation. All eight active WorkSpecs were
  migrated to schedule tag v2 and `NOT_REQUIRED` JobScheduler network type.
  The eleven cached presentations were byte-for-byte unchanged. During the
  one-time migration, old in-flight work was cancelled; two workers still
  recorded `ToolUnavailable` at 15:21:44 while DNS was UID-blocked. Three new
  workers returned retry about five seconds later without creating run records,
  consistent with their bounded network-readiness preflight. This does not
  establish successful background provider access: the device remained locked
  and the UID remained background-blocked. Two enqueued WorkSpecs temporarily
  had no corresponding SystemJob during the migration race. A controlled
  force-stop followed immediately by app start recovered eight SystemJobs for
  eight active WorkSpecs, without advancing the v2 generations or changing
  cached presentations. Filtered post-install log:
  `2026-10-15-sms901e-network-readiness-install.log`, SHA-256
  `8e80f9334985d039e7294b042a7128813620721ce92433096ec3e32f6dd6ebcf`.
- The full repository quality gate passed after the worker change (JVM tests,
  static checks, Android lint, debug/instrumentation builds, optimized R8,
  artifact checks and strict OpenSpec 10/10). The protected
  `connectedDebugAndroidTest` task was not run on the non-disposable phone.
- Follow-up at 17:37 local on the same installed `202610151514` APK: the run
  database recorded eight new `Success` outcomes after 15:22 (two at 15:47,
  one at 16:06, four at 16:50, and one at 17:09). Each corresponding cached
  presentation had a matching new completion timestamp. Android's activity
  event log shows ArarAI left the foreground at 15:25 and the display locked
  at 15:35; no ArarAI foreground activity appeared during those later runs.
  WorkManager still held eight active periodic definitions, each with a
  matching SystemJob for its current generation. This establishes unattended
  successful refresh on the installed build, but not exact hourly delivery or
  network-loss/recovery behavior. The app UID was again reported background-
  blocked at collection time; no network toggle was performed because it
  would sever wireless ADB. OpenSpec 9.4 remains open for its remaining cases.
- User-reported physical network-loss/recovery check on 2026-10-16 local:
  with Wi-Fi disabled, a manual refresh displayed an error while retaining the
  previous card; after Wi-Fi was re-enabled, refresh completed and the error
  disappeared. The Wi-Fi toggle invalidated the previous wireless ADB port;
  after reconnection on a new port, the app database corroborated the sequence
  for the same widget: `Success` at 22:00:24, `ControlledFailure` /
  `ToolUnavailable` at 22:00:47 and 22:00:59, then `Success` at 22:01:02.
  Its persisted presentation completion time advanced to 22:01:02. The
  intermediate stale-card display and cleared error were observed by the user,
  not independently captured by ADB during the offline interval. This covers
  the manual network-loss/recovery case; schedule replacement and worker/model
  telemetry remain to be checked before closing task 9.4.
- A separate opt-in physical probe at 22:20 local copied an existing confirmed
  hourly Wikipedia widget into a temporary instance without model generation,
  replaced its periodic interval from one to six hours, and deleted the copy.
  WorkManager retained one active unique work ID while advancing its generation
  0 to 1 and changing the schedule tag to six hours. Cleanup left all eleven
  original definitions/revisions/presentations byte-for-byte unchanged and all
  eight original active works matched to SystemJobs. The focused instrumentation
  test passed, and no LiteRT-LM event appeared in its window or a checked
  unattended-success window. The worker/provider source path invokes the
  coordinator and QuickJS without calling model load. Details are in
  `2026-10-16-sms901e-schedule-replacement.log`. Together with the earlier
  manual/background refresh, process recreation, stale-cache, link, disable,
  and deletion checks, this completes the physical cases in task 9.4. Android
  periodic timing remains inexact, as recorded above.
