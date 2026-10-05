# Background probe 4 — SM-S901E (2026-09-26)

- Tester: JJ (probe via UI button), evidence collected over ADB by JIA DEV.
- Device: Samsung Galaxy S22 (SM-S901E), arm64-v8a.
- Model: Gemma 4 E4B IT LiteRT-LM.
- Build: debug APK containing `f598ada` (background probe uses
  `forAuthoringCharacterization()`).
- Filtered log SHA-256:
  `1e6a551a4460b9c76a51c94eab29ab767be7e93cffc18b04d840ec361d181bfa`.
- Outcome: controlled failure after `engine_recovery_finished outcome=timed_out`.
  Nothing was saved or executed.

## Findings

- This run reaches real inference: feasibility captures are emitted for attempt
  1 (202 bytes) and attempt 2 (140 bytes). It is no longer rejected by the
  authoring-tools gate.
- Attempt 1 was a captured but invalid feasibility artifact
  (`InvalidFeasibilityDisplayName`), then the process was unloaded and the
  recovery gate passed in about 10 seconds.
- Attempt 2 reaches capture after about 43 seconds of generation and its
  cleanup. The next inter-generation recovery unloads successfully, with
  >3.2 GiB available memory and no low-memory flag, but does not become ready
  within the recovery gate's 3-minute bound.
- The final runtime telemetry has `thermalStatus=1` (LIGHT). The recovery gate
  currently requires thermal status NONE (0), plus battery <=32 C, process PSS
  <=1 GiB, >=20% free memory, and three consecutive 5-second samples. The
  filtered runtime telemetry does not include battery temperature, so that
  criterion remains unobserved; LIGHT thermal status alone is sufficient to
  explain the timeout.

## Interpretation

This is not the per-stage generation watchdog (`PER_STAGE_TIMEOUT_MILLIS = 90s`):
the second generation completed in about 43 seconds. The delay was the intended
recovery gate between pipeline generations, which timed out after its configured
3 minutes because its acceptance thresholds were not met.
