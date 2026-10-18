# Non-destructive SM-S901E manual validation — 2026-10-18

- Tester: JIA DEV via ADB, 17:37–17:53 UTC.
- Device: Samsung SM-S901E, Android 16/API 36, build
  `BP2A.250605.031.A3.S901EXXSEGZH4`, `arm64-v8a`.
- Source: uncommitted working tree based on `38acb8b`; debug APK
  `v202610172330`, SHA-256
  `a93799974f180c55cc546e2d10792d237c16d45623319c20c3f6f9c1e17352e8`.
- Before updating, the installed `v202610171006` APK was copied locally and
  its signing certificate SHA-256 matched the new APK. `adb install -r`
  succeeded without uninstalling or clearing app data. The local Gemma 4 E4B
  LiteRT-LM model remained 3,659,530,240 bytes before and after the run.
- Chat: two consecutive synthetic short generations completed with intact
  responses. A longer third generation was cancelled; the UI returned to
  `Modelo disponível`. Subsequent synthetic generations completed after
  cancellation. The conversation database showed completed assistant rows for
  the recovery prompts, and no assistant row for the cancelled prompt.
- Widget authoring: a synthetic staged workflow started a foreground service
  with an ongoing notification. Immediately after Chat, the process remained
  near 3.9 GiB PSS/3.1 GiB GL mtrack and the first attempt waited at the
  recovery gate. Cancelling it marked the attempt `Cancelled` and stopped the
  foreground service. A process-only restart reduced PSS to about 230 MiB;
  the persisted workflow was then resumed. Feasibility succeeded in 25,682 ms
  with 133 artifact bytes; after the unload/recovery/reload transition,
  Algorithm succeeded in 64,985 ms with 669 artifact bytes. Both reached
  `AwaitingReview` without a process crash. No test widget was confirmed or
  enabled.
- The feasibility artifact selected `wikipedia_on_this_day` for a request to
  display today's date. This is a model-output semantic mismatch, so this run
  is evidence for native/lifecycle behavior, **not** widget correctness or
  model eligibility.
- Cleanup: the synthetic workflow and sole synthetic Chat session were
  discarded through the app UI. No authoring foreground service remained.
  The app was left relaunched with roughly 211 MiB PSS and its model file
  intact. The exit-history entries from this run were the package update and
  deliberate process stops, not crashes.

Not executed: `connected*AndroidTest` is prohibited on this retained device
by `AGENTS.md` and `docs/device-validation.md` because cleanup can erase
app-private data. Rapid stale-notification/service transitions therefore remain
unproven on-device. Voice Chat was not exercised because no controlled
synthetic microphone input was available. The bounded downloader overflow is
covered by host fake-source tests, not a production-model transfer on-device.
OpenSpec tasks 1.3 and 4.5 remain unchecked; this run only partially covers
the latter's Chat/Voice/widget matrix.
