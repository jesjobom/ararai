# Background probe 1 — SM-S901E (2026-09-25)

- Tester: JJ (probe via UI button), evidence collected over ADB by JIA DEV.
- Device: Samsung Galaxy S22 (SM-S901E), Android 16 / API 36, arm64-v8a.
- Model: E4B (corrected; the introducing commit message wrongly cited E2B).
- Build: debug APK `app-debug.apk` built 2026-09-25 20:37 (commit 77094b3).
- Outcome: controlled `Feasibility/MissingArtifact`; nothing saved or executed.
- Logcat SHA-256: c76a18009e72c03ca5245a7b6d3ad7049347d4c16d1be4d663c29f75366780f1

## Positive background-job evidence

- Three model loads with two full unload/recover/reload barriers; each close
  released PSS from ~4.1 GiB to ~260-290 MiB; the recovery gate passed in ~10 s
  (thermal status 0, no low-memory signal) and each reload took ~17-20 s.
- The failure surfaced as a controlled in-app message; no partial state saved.

## Open observations

- Only two feasibility attempts appear in the log before the final
  MissingArtifact, while the pipeline budget allows three attempts (initial +
  two repairs, MissingArtifact is a repairable code). The probe path does not
  emit the `ArarAI.WidgetDiagnostic` lifecycle trace (it belongs to the
  diagnostic runner), so per-attempt outcomes cannot be distinguished from the
  log alone. Adding the sanitized lifecycle trace to the probe path is the
  follow-up.
- `missing_artifact` is new negative evidence for E4B (prior E4B evidence
  recorded feasibility contract failures and watchdog timeouts, not a missing
  artifact).
