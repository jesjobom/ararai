# SM-S901E debug APK installation — 2026-10-25

- Target: connected SM-S901E, Android package `com.jesjobom.ararai`.
- APK: `app/build/outputs/apk/debug/app-debug.apk`; SHA-256 `4b969fb2af4857ddeb5d61b10c826eda6583b275709223b77becd988827fb7a1`.
- Before: version code `29881565`, version name `202610242205`, last update `2026-10-24 22:07:05` (device time); first install `2026-10-04 22:04:26`.
- Action: `adb -s 192.168.0.13:37697 install -r app/build/outputs/apk/debug/app-debug.apk` returned `Success`. No uninstall or data-clearing command was used.
- After: version code `29882823`, version name `202610251903`, last update `2026-10-25 19:26:07` (device time); first install remained `2026-10-04 22:04:26`.
- Package installation was verified via Android package metadata. In-app visual or interaction behavior was not tested on the device.
