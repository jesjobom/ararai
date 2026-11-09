# Explicit-place weather revision: build and install

- The canonical `scripts/quality-gate.sh` passed, including Android unit tests, Detekt, lint, releaseCandidate assembly and strict OpenSpec validation.
- The releaseCandidate APK has SHA-256 `eee6a8450dc6634dca87f3b2fac1eebaecefff7eb9339f9ed9f2a948c4d8b955` and version `202611071651` (`versionCode=29901471`).
- `aapt dump permissions` showed no `ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION`, or `ACCESS_BACKGROUND_LOCATION` in this APK.
- `adb install -r` on the connected SM-S901E returned `Success`. The installed package reports version `202611071651`; `firstInstallTime` remained `2026-10-04 22:04:26`, confirming an in-place update rather than uninstall/reinstall.
- Real-model chat behavior and scheduled-widget behavior for this revision were not manually exercised on the device yet. Their focused contract/provider tests passed, but task 3.3 remains open.
