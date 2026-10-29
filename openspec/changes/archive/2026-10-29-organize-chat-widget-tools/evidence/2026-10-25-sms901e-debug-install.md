# SM-S901E debug APK installation — 2026-10-25 UTC

- Target: connected SM-S901E, Android package `com.jesjobom.ararai`.
- Device time zone: `America/Toronto` (EDT at installation).
- APK: `app/build/outputs/apk/debug/app-debug.apk`; SHA-256 `5c90863d95c87462bbc8d1cfcd6561fec3c5dd00d52a824691a00c76afad35ae`.
- Before: version `202610222211`, last update `2026-10-22 22:13:12` device time.
- Action: `adb install -r` returned `Success`; no uninstall or data-clearing command was used.
- After: version `202610242148`, last update `2026-10-24 21:57:06` device time; first install remained `2026-10-04 22:04:26`.
- Package path was present after installation. The package update was verified; in-app visual and interaction checks were not performed.

## Follow-up navigation correction

- After JJ requested the widget list as the direct Widgets destination, the corrected debug APK was rebuilt; SHA-256 `83103f62cca116a04d9313d0b9550a6dc17d8e1ecf0ca19c6a938a86db1a9c02`.
- `adb install -r` again returned `Success`. Package version advanced to `202610242205`, last update to `2026-10-24 22:07:05` device time, and first install remained `2026-10-04 22:04:26`.
- The full quality gate passed again after this correction. The device showed the notification shade during attempted observation; no in-app visual or interaction check was completed, and no lock-screen or notification UI was manipulated.
