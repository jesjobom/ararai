# Retained render-stage recovery diagnosis — 2026-10-29 UTC

Source: read-only copy of the retained SM-S901E app database, sanitized `ArarAI.Recovery` logcat samples, `dumpsys meminfo`, and the installed source tree. Raw prompt, generated source, and provider responses are not included.

- Installed debug version before repair: `202610272332`; app process PID `27861`.
- The active session had accepted feasibility, algorithm, call, and plan checkpoints. It was awaiting a retry of the model-authored `render` stage using `custom_source` and `web_search_content`. This was not the deterministic ten-link `web_search_top_results` path.
- The first render attempt ended with generation `TimedOut` after approximately 136 seconds, including cancellation/cleanup time. A later attempt ended with `RuntimeUnavailable` after interruption. Three subsequent retries ended with `DeviceRecoveryTimedOut` before generation.
- Recovery samples on 2026-10-28 at about 23:18–23:27 EDT reported process PSS around 3,650,000 KiB, available memory around 0.9–1.2 GiB out of 7.39 GiB, and `ready=false`; `dumpsys meminfo` attributed about 3,039,000 KiB of PSS to GL mtrack. Thermal status was `NONE` and the low-memory flag was false. The device therefore did not satisfy either the process-PSS or available-memory recovery criteria.
- `WidgetAuthoringStageRunner` previously called the recovery gate before releasing a shared engine session. Its `finally` block did not call unload when the gate timed out before a model load. The implementation now unloads before recovery sampling, consistent with the existing authoring spec, and has deterministic tests for retained and failed-unload sessions.

Physical retest:

- Full `scripts/quality-gate.sh` passed, including 860 JVM tests, lint, R8, artifact checks, and strict OpenSpec validation (16/16). Instrumented tests were compiled, not executed on the retained device.
- Debug APK SHA-256 `753e4faf7d39dd1b5eafed8d6ecbef74c58656cbd95c395f1c702f1eb77e9bb8` was installed with `adb install -r` as version `202610282341`. `firstInstallTime` remained `2026-10-04 22:04:26`; `lastUpdateTime` advanced from `2026-10-27 23:34:49` to `2026-10-28 23:48:49` EDT.
- At 23:50:53 EDT, a retained audio-profile model session used about 3,699,312 KiB PSS. The new preflight close finished in 955 ms; the first recovery sample was `ready=true` at 482,182 KiB PSS and 3,766,264 KiB available. Three stable samples completed at 23:51:04 EDT, after which the text-only model loaded and render generation started.
- That first retest's model completed in 49,092 ms, but Android `lmkd` reclaimed the app at 23:52:18 EDT for low swap and thrashing before the stage outcome was persisted. Navigation had inadvertently opened Voice Chat before this retry and loaded the shared engine in its audio profile; the result was therefore not a clean authoring-only run. Startup later recovered the attempt as interrupted.
- After restarting the app without entering Voice Chat, baseline PSS was about 244 MiB. The next render retry passed recovery and loaded the text-only model, but generation reached its separate 90-second watchdog. The app unloaded and survived. The retained session remains retryable; this is a custom-source render-generation limitation, not a repeat of the recovery-gate deadlock.
