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
real `ContentResolver` provider import, Activity stop/resume without downloading
a production model, and the isolated QuickJS runtime boundary.

## QuickJS widget runtime foundation

### Self-service release-candidate validator

When the development environment cannot retain an ADB connection to the
physical device, build and transfer this APK:

```sh
./gradlew assembleReleaseCandidate
scripts/verify-runtime-validation-apk.sh
```

Install `app/build/outputs/apk/releaseCandidate/app-releaseCandidate.apk` on a
supported arm64 device. Because the release candidate uses development signing
and the normal application ID, Android may require uninstalling an installed
copy signed by a different key; back up no private data for this validation and
do not remove an installation whose local data must be retained.

The release-candidate APK exposes a second launcher named **ArarAI Runtime
Validation** / **Validação do Runtime ArarAI**. Open it, tap **Run validation
matrix**, keep the activity in the foreground, and then copy or share the JSON
report. Review the report before sending it because it contains device and build
identifiers. It contains no prompts, credentials, JavaScript source, arbitrary
exception text, or stack traces.

Accept the self-service run only when `overallPassed` and `environment.accepted`
are both `true`, all 12 cases pass, `buildType` is `releaseCandidate`,
`supportedAbis` contains `arm64-v8a`, and both artifact hashes are nonzero. The
report records per-case duration and process PSS before/peak/after. Retain the
complete JSON as the evidence for OpenSpec tasks 1.2, 1.3, and 6.3.

This launcher executes the equivalent physical runtime/adversarial matrix from
inside the optimized application. Android does not permit an ordinary app to
start its own `androidTest` instrumentation package, so the ADB-driven suite
below remains available as an additional path rather than a prerequisite for
self-service validation. Host-native ASan/UBSan remains the evidence for raw
malformed UTF-8 at the private byte boundary; the public Android API accepts
Kotlin strings and the physical matrix validates malformed JSON and bounded
UTF-8 data.

APK assembly and a successful artifact check complete only preparation task
6.5. They do not complete physical tasks 1.2, 1.3, or 6.3 without a passing
exported report from an arm64 device.

### Accepted QuickJS foundation result — 2026-09-05

JJ ran the self-service validator on a Samsung SM-S942W with Android 16/API 36
and `arm64-v8a`. The report matched the distributed release-candidate APK SHA-256
`dbd660ec667760be23f3fb156b4089141a09d81c493678d8633adf6ce3d593e4`
and recorded the 1,071,696-byte QuickJS library SHA-256
`93a1c0716fe5a4596f3a29e2ca37e6a4b59134d1e713215212d7ff2fb5810e31`.

All 12 cases passed. The first data-only call completed in 15 ms, infinite-loop
termination and recovery completed in 55 ms, caller cancellation and recovery
completed in 26 ms, and the 50-isolate recovery case completed in 27 ms. Process
PSS was 65,524 KiB before the cold call and the highest sampled value was
100,864 KiB after the final two-phase case. Forbidden globals, prototype/host
access, imports, mutation, nondeterminism, malformed requests, output growth,
recursion, allocation pressure, cancellation, retained globals, sanitized
errors, and two-phase isolation all returned their expected bounded outcomes.

The canonical evidence is checked in at
`openspec/changes/archive/2026-09-05-add-sandboxed-javascript-widget-runtime/evidence/2026-09-05-samsung-sm-s942w.json`.
The received report file SHA-256 was
`5f5f160235d943713ba7c3e4c520fc9e77f0f151af294b98559620989da7e7c7`.
This result satisfies physical tasks 1.2, 1.3, and 6.3 for the foundation.

### ADB-driven instrumentation

Run the complete debug device suite first:

```sh
./gradlew connectedDebugAndroidTest
```

Then run the focused runtime suites against optimized targets:

```sh
./gradlew \
  -Pararai.appInstrumentationBuildType=releaseCandidate \
  :app:connectedReleaseCandidateAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.jesjobom.ararai.widget.runtime.WidgetRuntimeCoordinatorInstrumentedTest

./gradlew \
  -Pararai.quickJsInstrumentationBuildType=release \
  :quickjs-runtime:connectedReleaseAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.jesjobom.ararai.quickjs.QuickJsSandboxInstrumentedTest
```

Record the device model, Android/API version, ABI, build SHA and app version,
cold execution time, peak process memory, timeout and cancellation latency,
repeated-isolate result, APK delta, and packaged native ABI. Confirm ordinary
control flow and Unicode data-only arguments; immutable inputs and fresh
planning/render isolates; blocked constructor/prototype traversal, dynamic
evaluation, imports, host reflection, network, storage, process, clock, and
random access; bounded output, recursion, allocation, and infinite loops;
caller cancellation; sanitized errors; and successful recovery after every
failure class.

Do not mark OpenSpec tasks 1.2, 1.3, or 6.3 complete from APK assembly alone.
They require the recorded physical arm64 execution above.

## Managed widget vertical slice

The automated gate validates repository transactions, unique-work policy,
execution leases, provider fakes, proposal capture/validation, consent, cached
presentation decoding, URI policy, and a complete local-fake lifecycle. It does
not validate real local-model proposal quality or Android background behavior.

For OpenSpec tasks 9.3 and 12.4, first run the local **Tool-calling diagnostic**
for every candidate model. Suite v4 has five isolated schema cases
(`stage_feasibility_schema`, `stage_algorithm_schema`,
`stage_call_function_schema`, `stage_plan_function_schema`, and
`stage_render_function_schema`) followed by `complete_synthetic_pipeline`.
Every case must pass on the exact model/APK pair before adding
`widget_authoring_pipeline_v1` to that model's checked-in catalog entry. The
report remains local until explicit copy/share and contains no prompt, algorithm,
fragment, assembled source, tool arguments, provider content, exception, stack
trace, or credential. A complete-pipeline failure reports only its controlled
stage/code plus aggregate callback count and argument bytes per capture tool;
successful and non-stage terminal cases use null stage/code values.

After that gate, test the eligible artifact independently in English and
Portuguese. Record the exact prompt separately from the sanitized diagnostic,
model ID and artifact hash, app/build hash, stage timings, total generation
time, generation outcome, validation outcome, and time. Cover valid
creation, a complete edit, malformed/impossible behavior, disabled Wikipedia,
cancellation/navigation away, and a request for an arbitrary HTTPS endpoint.
Confirm every stage uses a fresh capture-only conversation, no normal Chat
session appears, no intermediate artifact is saved, and an unverified model
leaves authoring unavailable while its other declared workloads remain intact.

For task 9.4, confirm one Wikipedia widget and exercise:

- manual refresh and its canonical article link after an explicit tap;
- one periodic WorkManager execution, recording the actual inexact delay rather
  than treating the selected interval as an exact wall-clock promise;
- force-stop/process recreation followed by list/detail restore;
- network loss, controlled failure with retained stale content, then recovery;
- schedule replacement with exactly one remaining unique work definition;
- disable, verifying both manual and background execution stay blocked;
- confirmed deletion, verifying source/revisions/cache/observations/runs are
  gone and late work cannot recreate the widget; and
- process/runtime evidence that the background worker did not construct or load
  LiteRT-LM.

For the events-by-date correction, create a fresh widget from “show one random
Wikipedia event for today” in Portuguese and English. Confirm the proposal uses
`wikipedia_on_this_day@1` with numeric local month/day, the rendered card shows
one event year and event text rather than a date-index page, repeated manual
refreshes select only returned event items, and the link opens a canonical
related Wikipedia article only after a tap. Also verify normal Chat/Voice use
`wikipedia_on_this_day` for dated historical events and `wikipedia_pages` for a
direct biography or concept lookup; combined Wikipedia calls must stop at three
within one turn.

Record timestamps, final status, selected revision, sanitized run outcome,
Wikipedia host, and WorkManager state. Do not capture authoring prompts, complete
JavaScript source, provider response text, credentials, headers, or unrelated
local data in shared evidence.

Current status (2026-09-11): suite-v2 evidence below is retained as historical
transport characterization. The first physical suite-v3 run used debug APK
SHA-256 `4c0667b9f2c4e9f2bf9d56a5b1854c93158326ce48223ddd53121aa7b07984b8`
and E4B artifact SHA-256
`0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0`
on the Samsung SM-S942W. Model loading and the feasibility/call/plan/render
isolated cases passed; the isolated algorithm case failed with
`tool_call_parsing`, and the complete case captured 4,428 aggregate argument
bytes before returning the then-opaque `pipeline_invalid`. No timeout or raw
model content was reported. Suite v4 then ran on the same device and E4B
artifact with debug APK SHA-256
`c9d111a1507fa8e3af891fe8692ea559c8a3b3814926c5ed3d1a264ed62deaea`.
It reproduced the four isolated passes and the isolated algorithm
`tool_call_parsing` failure. The complete case localized all three captured
callbacks and all 4,428 argument bytes to `submit_widget_feasibility`, ending
after the initial attempt and two repairs with controlled stage/code
`feasibility`/`invalid_schema`. The report file SHA-256 was
`2b2e1b4b5d8c2f2ad5868b6d7c02366d1b9d03f890a664ffac707d9a71cb02ca`.
Consequently no checked-in model currently advertises the new authoring
protocol. The prior Portuguese E4B creation/manual run does not satisfy the new
gate, and the remaining PT/EN/edit/negative/background lifecycle matrix has not
been physically executed. OpenSpec tasks 9.3, 9.4, and 12.4 therefore remain
open.

### Historical suite-v2 evidence — 2026-09-07–10

The records below describe the retired one-shot `propose_widget` protocol. They
must not be interpreted as eligibility evidence for the staged protocol.

- Device testing with E2B and an effective Chat context of 6144 tokens reached
  the widget-authoring progress screen and then Android closed the application
  before a draft was shown. No terminal/ADB evidence was available, so this is
  recorded as an observed process-termination failure rather than an asserted
  LMK, OOM, or native crash classification.
- Code review found that the foreground authoring flow inherited the user's
  complete Chat context setting and had no generation watchdog. A large native
  KV-cache allocation was unnecessary for this bounded structured task, and a
  model that never submitted `propose_widget` could keep generating without an
  application-owned deadline.
- The follow-up limits widget authorship to at most 2048 context tokens without
  changing the saved Chat preference and cancels generation after 90 seconds
  with a controlled, localized failure. Automated regressions verify both the
  6144-to-2048 reduction and release of a timed-out ephemeral generation.
- This mitigation is not physical acceptance. Repeat the E2B case with the
  replacement APK; if Android still closes the process, retain `exit-info` and
  Logcat when terminal access becomes available. Task 9.3 remains open.
- The same prompt on the first replacement APK still closed the application,
  disproving the large context as the sole cause. A second review found that
  `propose_widget` resumed the authoring coroutine from LiteRT-LM's synchronous
  `OpenApiTool.execute()` callback and immediately cancelled the native
  generation. This could re-enter conversation cancellation before the callback
  returned, matching a previously observed LiteRT-LM lifecycle failure class.
- The next follow-up no longer accepts and cancels on the tool callback. It
  records at most the first proposal, lets the callback return, waits for the
  native terminal callback, and only then validates the proposal. A captured
  proposal whose generation never terminates is discarded by the 90-second
  watchdog. This lifecycle change also requires a new physical E2B retest.
- On the second replacement APK, the same E2B/6144/Portuguese prompt no longer
  closed the process. It reached a controlled proposal-rejection card and kept
  the authoring screen usable. The old generic card did not expose which local
  validation stage rejected the proposal, so this is evidence for lifecycle
  containment but not yet a valid-authoring pass.
- The next follow-up gives the dedicated structured task up to 4096 context
  tokens, caps its temperature at 0.2, and supplies a complete validated
  Wikipedia proposal example. It also maps sanitized draft failure categories
  (proposal format, JavaScript program, plan, tool arguments, or unavailable
  runtime) to distinct localized messages without retaining or displaying the
  rejected source. Repeat the same prompt and record either the draft or the
  specific controlled category. Tasks 9.3 and 9.4 remain open.
- On the 4096-token replacement APK, the same E2B/Portuguese prompt kept the
  application open but ended with the generic generation-failed card. That
  result proves the failure occurred before `propose_widget` delivered any
  arguments; the proposal parser, JavaScript validator, planner, and tool
  argument validator did not run.
- The next diagnostic APK classified the failure as LiteRT-LM structured
  tool-call parsing. Its attempt to reuse the production Firestore diagnostic
  reporter failed, and that integration was removed: a point-in-time physical
  validation should not introduce remote report persistence. The sanitized
  on-screen category is sufficient evidence for this failure class; ordinary
  Chat/Voice diagnostics keep their existing consent-gated reporting behavior.
  Tasks 9.3 and 9.4 remain open.
- The diagnostic build added an explicit **Tool-calling diagnostic** card to the
  widget authoring screen. Select an installed E2B or E4B model, open **Widgets →
  Create widget**, run the local diagnostic, keep the app foregrounded, then use
  **Share report** (preferred) or **Copy report**. Run each model independently.
  The five cases are `minimal_string_schema`, `flat_proposal_schema`,
  `full_schema_short_payload`, `production_context_flat_schema`, and
  `production_widget_schema`. Interpret the first failing stage as follows:
  minimal failure points to the model bundle/template/LiteRT-LM tool protocol;
  flat-only failure points to structured payload generation; a short-payload
  full-schema failure points to unsupported schema complexity; a flat-schema
  production-context failure points to context/source size or escaping; and a
  final-only failure points to their interaction. The matrix aborts after the
  first callback timeout instead of reusing that native runtime. The JSON includes
  APK, configured model artifact, and schema SHA-256 values plus bounded timing
  and outcome codes. It stays local until the user invokes Android sharing and
  excludes prompts, arguments, JavaScript, raw model output, exceptions, and
  stack traces.
- On the same Samsung SM-S942W, Android 16/API 36, and debug APK
  `a7d4425dfa32d532bc76d092a5636c736170ab00a43bfeebe9ed767bd0cf148b`,
  suite v2 completed without timeouts. E2B artifact
  `181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c`
  passed the first four cases, including the full schema with a 153-byte short
  payload and production context with a 1,071-byte flat call, but failed the
  complete production case in `tool_call_parsing` before a callback. E4B
  artifact `0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0`
  passed all five cases, including a 1,205-byte complete production callback.
- This evidence verifies the current production transport for E4B and identifies
  a practical E2B limitation rather than a general application or LiteRT-LM
  integration failure. The checked-in catalog therefore advertises
  `propose_widget` only for E4B in this version. It does not remove or change
  E2B's normal Chat, image/audio, reasoning, or application-tool capabilities.
  Real E4B proposal quality and the negative/lifecycle cases remain pending.
- A later APK with the structured `wikipedia_on_this_day@1` example exposed a
  second constraint. On the same device and E4B artifact, four consecutive
  suite-v2 runs at 2,048 context tokens passed the first four stages but ended
  the complete production stage with `no_tool_call`; there was no timeout or
  process termination. The full-schema/short-payload and
  production-context/flat-schema passes isolate the failure to the combined
  production workload. Authoring and its diagnostic now use a fixed isolated
  4,096-token budget instead of inheriting a smaller Chat preference. This
  correction has automated coverage.
- The replacement debug APK
  `a6fd7acd19b54215d0d8c0deeb42044a6d08275e7e64b9299be6af50e2aac570`
  restored the E4B gate on the same device: suite v2 reported the intended
  4,096-token context and passed all five cases. The complete production call
  delivered 1,311 argument bytes in 17,092 ms, with no timeout or process
  termination. This validates the context-budget correction; real PT/EN
  creation, edit, negative cases, and the background lifecycle still remain in
  tasks 9.3 and 9.4.
- The first real Portuguese request on that replacement APK reached draft
  planning but was rejected as `InvalidToolArguments`; nothing was saved or
  executed. This proves transport and program parsing succeeded but the model's
  plan did not preserve the Wikipedia input contract. The runtime context now
  provides `context.language`, `context.localMonth`, and `context.localDay`
  under the existing locale/local-time grants, and the reference source passes
  those values directly. The authoring instruction also forbids reparsing the
  raw locale/timestamp for tool arguments. Automated tests cover the bounded
  projection and Wikipedia fixture; replacement-APK physical validation is
  still required.

### 2026-09-07 managed-widget automated acceptance review

- The canonical quality gate passed with JDK alignment, 15 Firestore emulator
  tests, QuickJS ASan/UBSan and release host tests, formatting, Detekt, all JVM
  tests, Android lint, debug and instrumented-test APK assembly, optimized R8
  release-candidate assembly, R8 diagnostics, arm64 runtime-library inspection,
  and strict validation of all eight OpenSpec items.
- The generated release-candidate AboutLibraries inventory contains 186
  libraries and no library without a declared license identifier. Gradle used
  the checked-in dependency locks and strict verification metadata.
- Focused repository, scheduling/worker, runtime, tool, authoring, renderer,
  Compose, Chat, Voice Chat, and complete local-fake lifecycle coverage passed
  without live network access. The verified runtime-validation APK is
  `app-releaseCandidate.apk`, 54,795,039 bytes, SHA-256
  `5c0fa74f3b604c236d2586dd17a3f26aff1a7a78cc67b527455d9805c3e534fb`.
- Review against the sandbox-runtime contract and all four change delta specs
  found no Android launcher widget, currency/weather provider implementation,
  widget-controlled endpoint, remote synchronization, sharing/import,
  widget notification, or background model construction. Weather-like names
  that remain under widget test sources are synthetic protocol fixtures, not a
  registered provider.
- No physical-device evidence is claimed for this build. Real E2B/E4B proposal
  quality, Android background timing/process recreation, network recovery, and
  safe external article opening remain the explicit unexecuted checks in tasks
  9.3 and 9.4; final production acceptance and change archival stay blocked on
  those checks.

## LiteRT-LM

- Download and integrity-check each candidate LiteRT-LM model used by the release.
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
- With an image-capable model, open the in-app camera while speaking. Confirm the
  microphone and VAD continue, prior audio is retained, and opening or closing
  the camera restarts only the trailing-silence window.
- Let a valid pause capture a frame automatically, then repeat with a manual
  photo captured before speech finishes. Confirm the manual photo takes
  precedence, each camera closes after the current turn, and the next turn does
  not reopen it.
- Complete both a direct-audio turn and a Whisper-routed turn. Confirm the model
  and persisted message receive the photo with the matching audio/transcript and
  no abandoned draft file remains. Force camera capture failure and confirm the
  completed audio continues without being lost or repeated.

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

## Local calculator tool

Automated implementation evidence for `add-local-math-tool` was completed on
2026-08-08. The resulting debug APK is 116,238,682 bytes, 182,369 bytes larger
than the immediately preceding license-disclosure build.

JJ completed physical-device acceptance on 2026-08-08 with both validated Gemma
E2B and E4B bundles. Multiple calculation prompts were compared with the local
calculator disabled and enabled, and the enabled results were accepted. During
the disabled-tool pass, one model initially emitted a learned `call:math`
protocol-shaped string without executing the app's `calculator` tool. The app
was hardened to explicitly forbid calculator/math tool markup whenever the tool
is not advertised; the replacement APK was then retested with both models and
accepted.

No separate quantitative cold/warm latency, process-memory, thermal, or Android
installed-size measurements were retained from this acceptance pass. The APK
size delta above is the available binary-size evidence. Those quantitative
measurements remain release-validation checks rather than claims of this change.

- Test E2B and E4B independently with calculator disabled, then enabled. Record
  bundle hash and verify that only the validated model advertises `calculator`.
- Ask direct arithmetic, precedence, square-root, logarithm, and trigonometry
  questions in Chat and Voice Chat. Confirm tool selection, final synthesis, and
  no visible or spoken JSON/protocol/intermediate value.
- Confirm normal non-math prompts do not invoke calculation. Exercise invalid
  expressions, division by zero, excessive exponents, the three-call ceiling,
  cancellation, background/foreground, and model switching.
- Record cold/warm latency, responsiveness, memory, and installed APK size.
  Confirm expressions/results remain local and canonical history contains only
  the user message and final assistant response.

## Conversational generation configuration

- For E2B and E4B, open **Assistant configuration → Generation** and verify the
  selected model, catalog defaults, reasoning capability, and unavailable
  last-turn metrics before the first run.
- Set distinct context windows and temperatures for E2B and E4B. Switch models
  in both directions and restart the app; confirm each model restores its own
  values.
- Exercise Precise, Balanced, Creative, and a valid manual temperature. Confirm
  the next Chat and Voice Chat turns use the saved value and that benchmark
  parameters remain unchanged.
- Try progressively larger context windows. Record model, device, app build,
  load latency, memory pressure, ANR/process termination, and whether returning
  to the catalog default recovers normally.
- Confirm changing context closes incompatible runtime state and preserves
  canonical conversation history.
- Run a reasoning-heavy turn that finishes without final answer text. Confirm
  normal Chat shows Incomplete response, preserves partial reasoning under Show
  reasoning, and never rewrites truncated years automatically.
- Repeat in Voice Chat. Confirm no empty text, reasoning, ellipsis, or protocol
  content enters TTS; the incomplete message is visible in shared normal Chat
  history and the voice loop returns to a valid state.

## Experimental focused web search

- Use a debug build. Confirm Tavily and Exa begin unconfigured and disabled,
  the token field is obscured, and a failed verification does not select the
  provider.
- Supply a user-owned Tavily token, run verification, restart the process, and
  confirm the UI reports configured without displaying the token. Repeat with
  Exa.
- Select Tavily, then Exa. Confirm only one provider is selected, the next
  native conversation is recreated, and no request reaches the previous
  provider.
- Ask current, comparative, and technical questions. Confirm `web_search`
  returns at most three sources and the final answer appears without tool JSON
  or raw evidence. Repeat in Voice Chat and confirm only the final answer is
  spoken.
- Trigger invalid credentials, exhausted quota, rate limiting, offline mode,
  timeout, and cancellation. Confirm controlled recovery and no automatic call
  to the competing provider.
- Remove each credential and verify the provider is disabled immediately.
  Inspect sanitized logs and application data; no plaintext token,
  authorization header, query result body, or tool protocol may be retained.
- Release-build check: confirm unapproved `web_search` is not advertised even
  when a credential and selection remain stored.

## Media, permissions, storage, and privacy

- Deny microphone permission, retry, grant it, record, cancel, record again, and send.
- Deny camera permission, retry, grant it, cancel capture, and take a photo from
  normal Chat and Voice Chat. Confirm gallery selection remains available and no
  camera source or normalized draft is orphaned.
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

### 2026-08-19 release-candidate partial pass

- Tester: JIA DEV, automated over ADB.
- Device: Samsung Galaxy S22 (`SM-S901E`), Android 16 / API 36, arm64-v8a.
- Artifact: locally signed, R8-minified `releaseCandidate`, app version
  `v202608191647`, SHA-256
  `78895cc955161f13c884003b61bfafbfce49a1213b820aa599514dcd0daf48ff`.
- Model: installed Gemma 4 E2B IT LiteRT-LM artifact.
- Passed: cold startup, Home-to-Chat navigation, existing SQLite conversation
  restore, model initialization, completed local text generation (`R8_PASS`),
  benchmark retrieval, and process survival after generation.
- Automated device suite: all 30 debug instrumentation tests passed, covering
  lifecycle recreation, real `ContentResolver` image import, UI journeys,
  reporting UI states, credential encryption/failure handling, and Chat/Voice
  fake-provider parity without real service tokens. The current rerun used the
  app-scoped `en-US` locale on the `pt-BR` device because the Compose tests use
  literal English semantics. Twenty-nine tests passed together; the lifecycle
  test then passed separately after the fresh-install notification, microphone,
  and camera permission prompts were resolved.
- Current shrunk-runtime follow-up: the E2B model initialized through LiteRT-LM,
  selected the GPU/OpenCL delegate, completed a persisted Chat generation, and
  survived force-stop/restart. Home, Chat, Voice Chat, Models, Assistant
  configuration, and Settings all opened successfully. The on-demand diagnostic
  completed with 389 ms first-token latency, 11,069 ms generation time, 28
  prefill tokens at 82.37 tokens/s, and 92 decode tokens. Voice Chat reached
  Ready, entered Listening, and returned to Ready after Stop; Android
  AudioService recorded a matching `VOICE_RECOGNITION` start/stop pair.
- Follow-up live-provider validation used user-entered credentials without
  reading or exporting either token. Exa completed a focused web-search turn
  with three persisted Exa sources. After disabling Exa through the UI, Tavily
  remained enabled across a process restart and completed a turn with two
  persisted Tavily sources. The original Exa-preferred/Tavily-fallback
  configuration was restored afterward.
- A reported Voice Chat profile race was reproduced from the retained logs and
  corrected. Physical follow-up proved that entry from a text-only runtime now
  keeps Start disabled while showing model loading, then enables it only after
  the audio profile is initialized.
- A reported completed generation with no text or reasoning was confirmed in
  SQLite history. Empty completed output is now persisted and presented as an
  incomplete response instead of the ambiguous `...` placeholder.
- The first live-token follow-up exposed that the credential instrumentation
  tests reused production preference filenames and could clear credentials on
  an already configured debug installation. The tests now use isolated
  preference files; a focused rerun passed all three credential scenarios and
  byte-for-byte hashes proved the production preference files were unchanged.
- R8 defects found and corrected during this run: Room WorkManager database
  construction, EvalEx annotation discovery, and LiteRT-LM JNI access to
  `SamplerConfig` and `BenchmarkInfo`.
- Exclusions: Whisper model load/transcription, a repeated physical
  microphone/camera turn on the corrected build, new persisted media round trip
  in the release candidate, full Voice Chat, provider quota/rate-limit failures,
  and Firebase reporting through an official App Check distribution path. These
  exclusions keep OpenSpec task 3.2 open.

### 2026-08-22 release-shrinking acceptance follow-up

- The remaining local physical-device flows from the 2026-08-19 partial pass
  were confirmed as tested: Whisper model load/transcription, repeated
  microphone/camera use, persisted media round trip, and full Voice Chat.
- Provider quota/rate-limit failure handling and Firebase reporting through an
  official App Check distribution path were explicitly accepted as exclusions
  and were not executed.
- With those exclusions recorded, OpenSpec change
  `enable-safe-release-shrinking` task 3.2 is complete.

### 2026-08-22 Voice Chat navigation stress failure

- User validation repeatedly entered Voice Chat and immediately returned with
  the Android system Back action while model loading was still in progress.
- Result: the device became progressively slower and Android eventually closed
  the application. The earlier load-serialization fix was therefore
  insufficient and is not accepted as physical evidence.
- Follow-up implementation now requests the audio workload during the initial
  native engine load instead of creating and replacing an intermediate
  text-only engine. The same stress sequence still failed on the first
  follow-up build.
- Captured Logcat proves Android's low-memory killer terminated
  `com.jesjobom.ararai`: the process had 342,448 kB RSS and 854,212 kB swap,
  with 317% thrashing. Two audio-profile engine initializations ran in the same
  process. The first completed after its requesting screen had left, but its
  native session was not published before coroutine cancellation was
  re-observed; the next entry consequently started a second engine.
- The ownership transfer is now part of the same non-cancellable serialized
  transition as native initialization. A regression test cancels the original
  requester while bridge loading is suspended, completes loading, and proves
  that the next request reuses the published session with exactly one bridge
  load. Engine identity is logged on initialization and close for the next
  physical verification.
- Physical follow-up with the corrected ownership-transfer build repeated the
  original rapid Voice Chat entry/system-Back stress sequence. Voice Chat
  remained functional afterward and the application was not terminated.
- The follow-up exposed a presentation-only Chat issue after the audio profile
  was loaded: the persisted empty assistant placeholder was normalized as an
  incomplete response while the shared runtime was still switching back to the
  text profile. The final response replaced it correctly. Active generation
  now suppresses that terminal incomplete-response card; a genuinely empty
  completed generation still displays it after generation ends.
- Physical follow-up also confirmed that application startup and splash duration
  are materially improved on the test device. Together with the successful
  repeated Voice Chat navigation stress test, this completes OpenSpec change
  `harden-navigation-load-and-startup` physical validation task 2.4.
