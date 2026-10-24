# SM-S901E collection-card tool authorization failure and fix

- Read-only inspection of the app-private authoring database found one new
  session created `2026-10-08 03:59:51` UTC. Feasibility, algorithm,
  call-function and plan stages succeeded. The accepted algorithm chose
  `renderCapability=collection_card`.
- Render attempts 1–3 failed `MissingArtifact` between `04:03:14` and
  `04:04:23` UTC. Android logs show `generation_request_rejected` with
  `tools_unsupported` at the same times. The local engine rejected the new
  capture-tool name before invoking the model; no model-authored render
  artifact was generated. Wikipedia was not involved.
- Root cause: `submit_widget_collection_card` was present in the stage schema
  and request but absent from the model capability's closed set of ephemeral
  authoring tools. Added the name to that set and an engine-level test that
  exercises an advertised ephemeral collection-card request.
- Validation: targeted `LiteRtLmAuthoringToolTest` passed. The broader
  `spotlessCheck detekt testDebugUnitTest lintDebug assembleDebug` gate passed.
  The full repository quality gate had passed immediately before this narrow
  authorization fix; it was not rerun afterward. No connected Android
  instrumentation tests were run.
- Debug APK SHA-256: `d62b3fbc487fa8b3d12554b6fbfdd9d4d9d7367e875cfebff4ef3039eea52235`.
- Device: Samsung SM-S901E, serial `192.168.0.13:39167`. `adb install -r`
  returned `Success`; Android reports `versionName=202610080007` and
  `lastUpdateTime=2026-10-08 00:09:30` America/Toronto, advanced from
  `2026-10-07 23:48:53`. App data was preserved.
- Physical render retry with this corrected APK has not yet occurred. The
  existing session already has an accepted `collection_card` algorithm, so
  retrying its render stage will exercise the corrected permission path.
