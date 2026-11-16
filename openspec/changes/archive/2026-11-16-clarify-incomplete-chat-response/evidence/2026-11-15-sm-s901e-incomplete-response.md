# SM-S901E incomplete-response diagnosis and display — 2026-11-15 (UTC)

- Retained Samsung SM-S901E, Android 16; selected model: Gemma 4 E4B IT LiteRT-LM.
- The user-reported incomplete Chat turn was generated with APK `202611142116` at approximately 2026-11-14 23:36 EST. The device log recorded `model_done outcome=success`, not a native error or rejection. The app displayed a reasoning-only incomplete answer with attached tool sources. No prompt, reasoning text, or source URL is retained here.
- Assistant configuration → Generation showed the effective total context as 2048 tokens and the last-turn metrics as 535 prefill and 78 decode tokens. These counters do not establish full cumulative usage across tool phases. A later reproduction measured the native conversation at exactly 2048/2048 tokens, correcting the initial conclusion that context exhaustion was unlikely. LiteRT-LM did not expose a stop reason.
- Full `scripts/quality-gate.sh` passed, including 924 debug unit tests (zero failures or skips), lint, debug and releaseCandidate builds, artifact checks, and strict OpenSpec validation (11 items passed, none failed).
- New debug APK: version `202611142356`, versionCode `29911976`, SHA-256 `23d0ae58ba235efc51082935678f592fb952561a9624cbc409f9f17db1aadaae`.
- Updated with `adb install -r` (`Success`). Android reported `lastUpdateTime=2026-11-15 00:01:17` EST; `firstInstallTime=2026-10-04 22:04:26` was unchanged. No uninstall or storage clear was used.
- Reopened the existing incomplete conversation on the new APK. The banner now states that a tool ran but the model did not produce a final answer, with retry/rephrase guidance. It does not claim that the context limit was reached. The existing conversation and source list remained present. No new inference turn was run, so the near-capacity diagnostic path is covered only by host tests.

No `connected*AndroidTest` Gradle task was run on this data-bearing device. Screenshots were inspected live and discarded because the conversation contained user content.

## Follow-up: cumulative context and rejected fixed cap

- With 2,048 total context tokens, reasoning and web search enabled, the same synthetic Chat question reached **2,048/2,048** cumulative native tokens and ended with no final answer. The SDK completion callback still reported success; it did not expose a stop reason.
- An A/B build limiting only model-visible web evidence to 600 characters returned an answer with a source at **1,936/2,048**. Debug APK `202611150214` (versionCode `29912114`, SHA-256 `9ee8c1cd96bcd66200e1e559b8df7ede77175ebf0f042e522c4b84582ffe8f20`) repeated that result on the device. The full gate passed with 926 unit tests and 12 strict OpenSpec items.
- The fixed 600-character cut was rejected because it discards evidence even when a larger context is available. It has been removed from the implementation and is **not** the intended behavior. The existing 1,800-character normalized reference limit remains; the incomplete-response message is the intended response to likely context exhaustion.
- That APK was installed with `adb install -r`, preserving `firstInstallTime=2026-10-04 22:04:26` local device time. Synthetic titled Chat sessions were removed afterward; original Portuguese conversations remained. No uninstall, storage clear, or `connected*AndroidTest` task was used.

## Restored full evidence, first device check

- Debug APK `202611151121` (SHA-256 `51fa2fd01c51346b2d97d0d73a1feeb80d2c0dfefec907cdc94132e897a6fb44`) restored the original normalized evidence result without the second 600-character cut. Installed with `adb install -r`; first-install timestamp was unchanged.
- The same synthetic question reached **2,048/2,048** native tokens and persisted an incomplete assistant turn with `ContextCapacityLikely`. With `Show reasoning` temporarily off, Chat displayed the localized message recommending a larger context, disabled reasoning, or a new chat. `Show reasoning` was restored to its prior on setting; the synthetic session was deleted.
- With the long reasoning visible, the banner followed it and was not immediately visible in the phone's initial viewport. The banner is being moved before reasoning so the explanation remains discoverable; this placement was not verified in the first APK.

## Final device check: banner before reasoning

- Debug APK `202611151133` (SHA-256 `b7d737a4ac8a9eabbacfe08e37a108c33104f9e6200e5111fcfa350e4d7f694b`) installed with `adb install -r`. Android reported `lastUpdateTime=2026-11-15 11:34:20` and unchanged `firstInstallTime=2026-10-04 22:04:26` in device local time.
- Repeating the same synthetic question with reasoning display on again reached **2,048/2,048** native tokens and no final answer. The initial Chat viewport showed `Incomplete response` and the actionable context-capacity explanation **above** the `Reasoning` heading. No silent second generation or web-evidence reduction was involved.
- The synthetic Chat session was deleted. Original conversations and the user's reasoning-display preference were left unchanged. No screenshot, source URL, generated text, or private database copy was retained.

## Follow-up: real conversation warning obscured by auto-scroll

- The latest user-reported turn (2026-11-15 14:25 EST) ended with a native count of **2,049/2,048** configured context tokens. LiteRT-LM again called completion successfully; it did not report a stop reason.
- Read-only inspection of message metadata (not prompt or answer content) found the latest assistant text empty, with persisted `Incomplete` status and `ContextCapacityLikely` reason. The explanation existed at the beginning of the message, but Chat's follow-latest scrolling moved the viewport below it.
- The warning is now also derived from the latest persisted assistant message and displayed beside the input. Focused ChatViewModel tests passed. `spotlessCheck detekt lintDebug assembleDebug` passed; strict OpenSpec validation passed 11/11.
- Debug APK version `202611151437`, versionCode `29912857`, SHA-256 `60da20621badf3bab5304ae0546fbafc61c6a00a0a5a399b76b33ceca474bca0`, installed with `adb install -r`. Android reported `lastUpdateTime=2026-11-15 14:40:10` EST and unchanged `firstInstallTime=2026-10-04 22:04:26` EST.
- Reopening the same Chat conversation showed the context-capacity guidance near the input in the visible viewport, while the original in-message banner remained above the scrolled content. The saved conversation was not edited or deleted. No new model turn was run; the test validates visibility for the observed persisted failure, not a future LiteRT-LM completion.
