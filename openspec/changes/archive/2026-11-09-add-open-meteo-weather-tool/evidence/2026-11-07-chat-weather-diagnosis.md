# Weather chat failure on SM-S901E (2026-11-07)

## Original installed build

- Release-candidate versionCode `29900365`, versionName `202611062225`.
- The first location-less request selected Android's network provider at 01:03:22 device time. Location service reported zero fixes before the weather request's 15-second timeout. The app did not reach Open-Meteo for that request.
- Two model generations ended; the third reported `Conversation ready: reused=true` at 01:04:54, then no request-submitted, first-callback, crash, ANR, or LMK record. The release build did not permit a thread dump through `run-as`, `debuggerd`, or `kill -3` from ADB.

## Instrumented reproduction

- Installed a data-preserving diagnostic build with sanitized markers around conversation activation, request construction, native submission, and weather execution. No prompts, tool arguments, or coordinates were logged by these markers.
- In a new chat, the first explicit-place weather invocation completed successfully in 1.47 seconds and produced a complete answer.
- The second invocation completed successfully in 0.36 seconds, but the model produced a truncated answer. Its prefill was 1,272 tokens, consistent with the large 24-hour tool payload contributing to pressure on the 2,048-token context. This is a likely contributor, not proven sole cause.
- At 03:44:19 device time, the third request reached `Submitting model request: reused=true` but never reached the marker after `Conversation.sendMessageAsync`. No weather-tool start marker occurred. This reproduces the user's hanging generation and localizes the block to native conversation reuse, not Open-Meteo or device location.

## Fix and verification plan

- Prefer Android's fused provider for coarse device location on API 31+, falling back to the network provider when fused is disabled.
- Disable native conversation reuse while the weather tool is enabled; reconstruct each turn from the persisted chat transcript.
- Send local models six-hour rain windows instead of 24 raw hourly entries. Widget consumers keep the full structured result.
- Run the repository quality gate, install with `adb install -r`, and repeat a three-turn chat on the connected device. Do not clear app data or run connected Android tests.

## Verification result

- Repository `scripts/quality-gate.sh` passed end-to-end, including unit tests, Detekt, Spotless, Android lint/builds, release artifact checks, and strict OpenSpec validation (10 passed, 0 failed).
- Final release-candidate APK SHA-256: `0d574b843a4ff9e67ce7fe44564a8f658fc51d5470928095d5cccaa56305c257`; installed with `adb install -r` (`Success`). Installed versionCode `29900694`, versionName `202611070354`. The original `firstInstallTime` remained `2026-10-04 22:04:26` (device time), confirming data-preserving replacement.
- On the final build, a new chat answered an explicit-place temperature question after a successful weather tool call (1.17 s). A second rain-forecast request completed with a coherent sourced answer and a successful tool call (0.26 s); model prefill fell from 1,272 tokens in the original 24-hour-payload reproduction to 365 tokens with rain windows. The third turn submitted with `reused=false` and completed instead of hanging. It used the recent temperature already in chat rather than making another provider call.
- A separate new chat asked for current local temperature without a place. With fused location selected, the weather tool completed successfully in 0.77 s and the model returned a temperature. The device was still in airplane mode with Wi-Fi enabled; no precise coordinate was captured in this evidence.
- Four diagnostic chat sessions created during testing were removed. The user's original weather session and unrelated chat history were left intact.
