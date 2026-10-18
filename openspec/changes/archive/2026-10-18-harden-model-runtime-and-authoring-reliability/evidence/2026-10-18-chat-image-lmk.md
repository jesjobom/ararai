# SM-S901E Chat image low-memory exits — 2026-10-18

- JJ reported two successful audio-in-Chat tests (one without transcription,
  one with transcription) followed by three image attempts in which the app
  closed. Audio outcomes are user-reported, not independently replayed here.
- Device: SM-S901E, Android 16/API 36, debug app `v202610172330`, Gemma 4
  E4B LiteRT-LM. Times below are device-local `America/Toronto` (EDT).
- Android `ApplicationExitInfo` records three app exits with
  `reason=LOW_MEMORY` at 14:03:53, 14:05:36, and 14:09:27. The event buffer
  confirms the final process died without activity state saved. No Java
  exception or native crash trace was present in the exit records.
- The three app-owned JPEGs left by the attempts are each 768×1024 pixels,
  with encoded sizes of 104,196, 105,647, and 115,231 bytes. Only dimensions
  and byte counts were inspected; no image content was exported or included.
  The importer bounds source data to 20 MiB and 8192 px, then normalizes its
  largest side to at most 1024 px before passing `Content.ImageFile` to
  LiteRT-LM. The source-photo dimensions are unknown.
- After these attempts, a loaded app process showed about 4.41 GiB total
  PSS, including about 3.48 GiB GL mtrack, with about 0.94 GiB system
  `MemAvailable`; battery temperature was 42.4°C. These are post-attempt
  measurements, not peak-at-kill measurements, and cannot establish the exact
  allocation that triggered LMK.
- After the phone cooled, JJ retried an image with E4B; Android again recorded
  `LOW_MEMORY` at 14:38:54. The app-owned JPEG from that attempt was 768×1024
  pixels and 105,644 bytes. JJ then reported a successful image exchange with
  E2B on the same phone; its app-owned JPEG was also 768×1024 pixels (111,760
  bytes). With E2B selected afterward, the app measured about 3.07 GiB PSS,
  including 2.02 GiB GL mtrack. These measurements were taken in different
  process states and are not a controlled peak-memory comparison.
- Git history places the 1024 px image normalization, `Content.ImageFile`
  mapping, vision-backend selection, and E4B model artifact configuration
  before managed-widget development. The widget work did modify the shared
  inference engine (including generation serialization/lifecycle behavior),
  so an indirect regression cannot be ruled out without an A/B run. Archived
  physical-device acceptance for images does not name both E4B and SM-S901E;
  no record found establishes that this combination previously succeeded.

Conclusion: the three closures were Android low-memory kills, not evidence of
a too-small text context window. Original photo resolution alone is not the
cause because the app already downsamples to 1024 px. Whether **1024 px is
still too costly for the vision backend on this device** remains unproven.
Increasing the context budget is not a remedy and may raise memory use. The
same 1024 px normalized dimensions succeeding with E2B but failing with E4B
make model/vision memory headroom the leading hypothesis; this is not proof
that E4B image inference never worked on this device or that widget work
caused the failure.

Next controlled experiment: after thermal normalization and a process-only
restart, compare one synthetic image at smaller normalized bounds (for
example 256/512 px) with the current 1024 px path while recording process
PSS/GL memory, system available memory, terminal result, and exit reason.
Change the production cap only if a smaller size materially avoids LMK and
retains acceptable vision quality. Do not repeat image generation while the
device remains hot or erase the retained installation for instrumentation.

## 512 px diagnostic build installed

- Built a temporary debug variant with `NORMALIZED_CHAT_IMAGE_DIMENSION = 512`.
  `ChatImageImporterTest` and `assembleDebug` passed. APK version
  `202610181449`, SHA-256
  `5d5f8be62f243cab77cbce85e4c2ba0c49972143645a1e82dcc4244d43b76f28`.
- Installed with `adb install -r` on the SM-S901E. Package inspection confirmed
  `lastUpdateTime=2026-10-18 14:50:30` (device-local EDT) and the new version.
  Both E2B and E4B model files remained under app-private storage.
- Restored the source constant to 1024 px after installation; the 512 px value
  is present only in the installed diagnostic APK, not in the source tree.
  Real E4B image inference with this APK is pending a user trial. No
  `connectedAndroidTest` task was run on the retained device.

## User trial and pre-widget A/B setup

- JJ reported that the 512 px diagnostic APK remained unreliable with E4B:
  only with context reduced to 2048 tokens did an image turn produce a
  response, and the app then closed. `ApplicationExitInfo` recorded additional
  `LOW_MEMORY` exits at 15:24:44, 15:26:11, and 15:28:26 device-local EDT.
  The exact exit corresponding to the successful response is not identified.
- Built pre-widget `main` commit `c9de4eb` as an isolated debug application
  (`com.jesjobom.ararai.abmain`, label `ArarAI Main A/B`) so the retained app
  and widget data need not be downgraded. The test-only package and label edits
  were reverted from the clean worktree after the build. The installed APK is
  version `202610181532`, SHA-256
  `75e1cc9c1b10e34732b7d3678b3a1ed0e0d47e87647ae2ac241f5d3993f47ad1`.
  `ChatImageImporterTest` and `assembleDebug` passed.
- Copied the E4B bundle between the two app sandboxes on the phone; the clone
  has the expected 3,659,530,240 bytes and SHA-256
  `0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0`.
  It appears as ready in the clone. The original package remained installed at
  version `202610181449`. A temporary redundant `.part` download in the clone
  was stopped and removed; the verified bundle was retained. A neutral
  1024×768 PNG fixture was placed in Pictures for an optional controlled trial.
- JJ volunteered to perform the E4B image trial in the isolated `main` app;
  the observed result is recorded below.

## Pre-widget A/B result

- JJ reported that the synthetic image produced a description, followed by an
  app closure. A newly captured photo did not produce a description.
- Android recorded `LOW_MEMORY` exits for the isolated pre-widget app at
  15:40:39 and 15:42:51 device-local EDT. The clone retained two normalized
  JPEGs: 768×1024 (91,179 bytes) and 1024×768 (8,252 bytes). Only dimensions,
  byte counts, and timestamps were inspected; photo contents were not viewed
  or retained in the repository. The importer in this `main` build uses the
  original 1024 px bound.
- This reproduces the E4B image instability without managed-widget code on
  the same SM-S901E and with the same model bundle. Managed-widget development
  is therefore not a necessary cause of the observed low-memory failures.
  It does not prove that every earlier revision, image, backend, or E4B-capable
  phone will behave identically. Operationally, E4B image inference with this
  LiteRT-LM configuration is **not reliable on this device**; E2B remains the
  observed working image path. Do not promote a global E4B capability change
  based on one device; a device/workload-specific guardrail is separate work.

## Diagnostic cleanup and current-build restoration

- JJ requested removal of the isolated pre-widget app. Uninstalled only
  `com.jesjobom.ararai.abmain` and removed the synthetic picture/temporary UI
  dumps created for the A/B test. Removed the clean temporary `main` worktree.
  The original package was not uninstalled.
- Rebuilt the current widget-enabled source with the restored **1024 px**
  importer constant. `ChatImageImporterTest` and `assembleDebug` passed;
  inspected compiled `ChatImageImporterKt` and confirmed constant `1024`.
  Debug APK `202610181550`, SHA-256
  `9ef72c02bc18551c2d33c7510fd92e1e5dd97a4b1dcafade45c2ff3a77cded3d`.
- `adb install -r` succeeded. Android reported
  `lastUpdateTime=2026-10-18 15:51:37` EDT and unchanged original
  `firstInstallTime=2026-10-04 22:04:26`. Both retained E2B/E4B artifacts and
  the managed-widget database remained present. The `.abmain` package is absent.
  This verifies installation and data retention, not a fresh inference pass.
