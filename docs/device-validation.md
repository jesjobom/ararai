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
real `ContentResolver` provider import, and Activity stop/resume without
downloading a production model.

## LiteRT-LM

- Download and integrity-check each candidate LiteRT-LM model used by the release.
- For a quick provider check, open **Instructions and tools**, select the
  **Tools** tab, and run the Wikipedia smoke test. It calls the provider
  directly with a fixed query and does not load or prompt the model.
- Validate model-driven tool selection through normal Chat and Voice Chat.
  Confirm English-first search, fallback to the question language, controlled
  failure, the three-call ceiling, and absence of visible protocol content.
- Test E2B and E4B independently and record each bundle hash plus device/build
  details. Do not add tool capability metadata to a catalog entry solely
  because another bundle in the same family passed.
- Run text, image, audio, and reasoning cases only where the model declares support.
- Confirm the reported acceleration/backend and capture TTFT/decode metrics.
- Cancel during active generation, run again, switch Gemma variants, and unload.
  Confirm the prior conversation is not reused incompatibly and memory recovers.
- Repeat ten short generations, then one context-heavy generation.
- Background/foreground, lock/unlock, and return through recents during load and
  generation. Confirm no duplicate generation and recoverable UI state.

## Voice Chat v0

- Use an audio-capable LiteRT-LM model; confirm unsupported models disable Start
  and link to model management.
- Deny microphone permission, grant it on retry, start/stop while listening, and
  leave the screen in every phase. Confirm no recording or TTS continues.
- Compare WebRTC and Silero with the same ten synthetic turns in quiet, fan/TV,
  street, near/far, and multilingual conditions. Record false starts, false
  endings, missed endings, pause latency, CPU, memory, battery, and temperature.
- Start with the experimental baseline of Silero/Aggressive, 300 ms speech
  confirmation, 300 ms pre-roll, and 500 ms minimum speech. Vary one setting at
  a time and record the effective values with each result.
- Compare MIC, VOICE_RECOGNITION, and VOICE_COMMUNICATION with native noise
  suppression requested and disabled. Record the effective source/effect shown
  by diagnostics rather than assuming vendor support.
- Confirm the microphone is inactive during model processing/TTS, response
  segments remain ordered, Stop flushes speech, and no callback restarts work.
- After completion, failure, Stop, activity destruction, and forced process death,
  verify Voice Chat WAVs are absent after the next launch and Chat media remains.
- Confirm Voice Chat uses the selected persisted session shared with normal
  Chat, carries bounded reconstructible history across modes, and clears only
  transient diagnostics when its owner is destroyed.

## Wikipedia skill

- Test both validated Gemma 4 E2B and E4B bundles with Wikipedia disabled.
  Open Chat, Voice Chat, settings, and existing history; confirm there is no
  research indicator or Wikipedia request.
- Enable Wikipedia and submit a direct non-research prompt. Confirm the model
  answers without a request or source links.
- Ask explicitly for Wikipedia information in English and Portuguese. Confirm
  the transient research indicator, one request, a final answer, and at most
  three official clickable source links.
- While research is active, confirm Voice Chat microphone capture remains
  inactive and no JSON, extract, function name, or tool protocol is spoken.
- Open the Voice-created answer in normal Chat, restart the process, and verify
  that source links remain attached to the answer.
- Disable networking and repeat an eligible request. Then test cancellation
  during research. Confirm controlled recovery, no retry, no partial source
  metadata, and a usable next turn.
- Switch Wikipedia off, change between E2B/E4B and an unsupported model, edit
  the Chat and Voice instructions, and switch modes. Confirm the retained
  native conversation is recreated only when compatibility changes.

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
