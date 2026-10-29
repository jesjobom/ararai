# SM-S901E debug APK update — 2026-10-26 UTC

- Target: connected SM-S901E, package `com.jesjobom.ararai`.
- APK: `app/build/outputs/apk/debug/app-debug.apk`; SHA-256 `157eae226a31fd8f56b78ea6a83bb01da1be6c6aad088cfed62fffb8160edc49`.
- Before: version code `29882823`, version name `202610251903`, last update `2026-10-25 19:26:07` (device time); first install `2026-10-04 22:04:26`.
- Action: `adb -s 192.168.0.13:37697 install -r app/build/outputs/apk/debug/app-debug.apk` returned `Success`. No uninstall or data-clearing command was used.
- After: version code `29883081`, version name `202610252321`, last update `2026-10-25 23:29:06` (device time); first install remained `2026-10-04 22:04:26`.
- Installation was verified via Android package metadata. In-app visual and interaction checks were not performed by the agent.
