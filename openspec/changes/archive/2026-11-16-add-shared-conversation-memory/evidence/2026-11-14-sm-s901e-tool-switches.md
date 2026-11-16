# SM-S901E tool-switch validation — 2026-11-14 (EST)

- Tester: JIA DEV; retained Samsung SM-S901E, Android 16; selected model: Gemma 4 E4B IT LiteRT-LM.
- Source checkout: `fd06df8` plus the uncommitted `add-shared-conversation-memory` change.
- Full `scripts/quality-gate.sh` passed, including 921 debug unit tests with zero failures or skips, lint, debug and releaseCandidate builds, artifact verification, and strict OpenSpec validation (10 items passed, none failed).
- Debug APK: version `202611142116`, versionCode `29911816`, SHA-256 `474125c60bcced32054a0872f79f627b2f3beb542a7f8986d1cd096e422ae3b4`.
- Updated with `adb install -r` (`Success`). Android reported `lastUpdateTime=2026-11-14 21:24:02` EST; `firstInstallTime=2026-10-04 22:04:26` did not change. No uninstall or storage clear was used.

## Observed checks

1. Assistant configuration → Tools showed **Shared memory** as an available, enabled switch for the selected model. Its description directs users to Assistant configuration → Context to view, edit, and delete saved memories, and explains that switching off writing does not delete existing entries or hide them from later conversations.
2. **Local calculator** was also enabled on first opening Tools after upgrading the APK. Both existing user-preference stores are covered by host tests for default-on and explicit opt-out behavior.
3. Turned Shared memory off, navigated to Context and back to Tools, and observed that it remained off. Turned the calculator off and observed its off state. Turned both back on and visually confirmed the enabled state. No pre-existing memory or chat data was modified.

This was a non-destructive switch/UI test. It did not repeat a model inference turn, test Voice Chat, or exercise on-device capacity rotation; the earlier shared-memory evidence covers the physical Chat write and cross-session recall. The screenshots were inspected live but not retained. No `connected*AndroidTest` Gradle task was run on this retained installation.
