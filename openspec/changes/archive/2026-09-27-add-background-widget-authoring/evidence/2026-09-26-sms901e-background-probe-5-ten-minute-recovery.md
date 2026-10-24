# Background probe 5 — 10-minute recovery window (SM-S901E, 2026-09-26)

- Tester: JIA DEV, probe started and observed over ADB.
- Device: Samsung Galaxy S22 (SM-S901E), arm64-v8a.
- Model: Gemma 4 E4B IT LiteRT-LM.
- Build: debug APK from the working tree based on `aa29d6e`; APK SHA-256
  `c7fc1265de5cd6777c1612c0be8b9eb5e1dd5ac3b34ca771d1e6ff95df8dc9da`.
- Installed version: `202609261556`; `lastUpdateTime` advanced to
  `2026-09-26 15:57:18` on the device.
- Filtered log SHA-256:
  `ab5effb3c32d499cb9c14e4e038ce566e9f621a0fc0c0cf8c9a373c9448c260b`.
- Timeout-change outcome: pass.
- Complete-probe outcome: interrupted by Android LMK during the model reload
  after recovery; no terminal pipeline result was produced.

## Findings

- Feasibility attempt 1 captured 202 bytes and failed controlled validation
  with `InvalidFeasibilityDisplayName`. Its recovery gate passed normally.
- Feasibility repair attempt 2 captured 140 bytes. The following recovery gate
  began with battery temperature 34.2 C while thermal status, PSS, available
  memory, and the low-memory flag were acceptable.
- The app was moved to the launcher while the foreground-service job continued.
- The gate was still sampling `ready=false` at 16:03:31, more than 3 minutes 14
  seconds after its first sample at 16:00:16. There was no
  `engine_recovery_finished outcome=timed_out` at the former 3-minute bound.
- Battery temperature later fell to 30.9 C. Three consecutive accepted samples
  completed at 16:04:27, and `recovery_finished ready=true` was followed by
  `engine_recovery_reload_started`. The accepted wait was about 4 minutes 11
  seconds, directly proving the extended recovery window.
- During the subsequent E4B initialization Android LMK killed the foreground
  service process (`oom_score_adj 200`) because the low watermark was breached
  and swap was low. This is distinct from the recovery timeout change and keeps
  the full background-authoring run from being accepted end to end.

## Interpretation

The requested 10-minute recovery bound works on the physical device and allows
a recovery window that the former 3-minute bound would have rejected. The run
also exposed a separate background model-reload memory-pressure failure. The
timeout change should not be described as a complete background-authoring pass
until the LMK risk is addressed or a later full run completes.
