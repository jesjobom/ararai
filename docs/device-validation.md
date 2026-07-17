# ArarAI physical-device validation

Use this matrix for release candidates and changes that touch inference, native
libraries, media, lifecycle, permissions, or performance. Use only synthetic
prompts and media; never attach private conversations, logs, or user files.

## Record before testing

- Date and tester:
- Git commit and app version:
- APK source (local build or CI run):
- Device model, Android version, build number, RAM, and free storage:
- Battery level and charging state:
- Ambient/start device temperature when available:
- Model name, artifact hash/version, runtime, and acceleration policy:
- Skipped checks and reason:

## Automated device gate

1. Connect an arm64 device with USB debugging enabled.
2. Run `adb devices` and confirm exactly the intended device is authorized.
3. Run `./gradlew connectedDebugAndroidTest`.
4. Retain the generated test report, but inspect it before sharing to ensure it
   contains no private device data.

The instrumentation suite checks manifest permission/backup configuration, a
real `ContentResolver` provider import, Activity stop/resume, and JNI library and
symbol loading without downloading a production model.

## llama.cpp / GGUF

- Install the candidate cleanly and verify first launch without a model.
- Download a configured GGUF; cancel once mid-download, retry, and verify no stale
  `.part` file blocks completion.
- Run a synthetic text prompt on CPU-only and record load time, TTFT, decode rate,
  peak observed memory, and completion status.
- Run the same prompt with GPU-preferred acceleration. Record actual backend from
  logs and note fallback; do not infer GPU use from UI configuration alone.
- Cancel during generation, immediately run again, and confirm no crash, stale
  output, or retained busy state.
- Run ten consecutive short generations and watch memory trend and failures.
- Background/foreground during generation, rotate if supported, lock/unlock, and
  return through Android recents. Verify partial output durability and controls.

## LiteRT-LM

- Download and integrity-check each candidate LiteRT-LM model used by the release.
- Run text, image, audio, and reasoning cases only where the model declares support.
- Confirm the reported acceleration/backend and capture TTFT/decode metrics.
- Cancel during active generation, run again, switch model/runtime, and unload.
  Confirm the prior conversation is not reused incompatibly and memory recovers.
- Repeat ten short generations, then one context-heavy generation.
- Background/foreground, lock/unlock, and return through recents during load and
  generation. Confirm no duplicate generation and recoverable UI state.

## Media, permissions, storage, and privacy

- Deny microphone permission, retry, grant it, record, cancel, record again, and send.
- Import a valid image, malformed image, image over 20 MB, and image over 8192 px
  on one axis. Confirm controlled errors and no orphan temporary files.
- Delete a media session and clear all sessions; verify storage decreases and media
  shared by another message remains until its final reference is removed.
- Confirm Android backup reports the app as ineligible and a device-transfer test
  does not restore conversations, media, models, or preferences.

## Memory and thermal observation

- Start from a cool device and record initial temperature/memory when available.
- Run continuous representative prompts for at least 15 minutes per runtime.
- Record temperature, throttling symptoms, decode-rate trend, crashes, ANRs, and
  whether Android kills background processes. Never claim a thermal pass from CI.

## Result

- Overall: pass / fail / pass with exclusions
- Failed checks and issue links:
- Environment-only exclusions:
- Sanitized artifact locations:
- Reviewer and date:
