# SM-S901E Wikipedia page summary v2 install

- Date: 2026-10-09 UTC.
- Change: widget-only `wikipedia_pages@2` searches up to ten Wikipedia page
  candidates using metadata, selects `first` or `seeded_random`, and fetches
  only the selected page's complete lead introduction and canonical URL.
  Existing Chat and saved widget `wikipedia_pages@1` remain registered.
- Quality gate: `scripts/quality-gate.sh` succeeded. 757 JVM tests, 15
  Firestore emulator tests, 10 runtime-helper tests, Android lint, all debug
  and instrumentation builds, release-candidate/R8 artifact checks, and
  `openspec validate --all --strict` (11 passed).
- Debug APK SHA-256:
  `8cf8a70c6d5e8815ef8e1f2de2bf915cc67391f45ed5824e92342439870da1b6`.
- Install command: `adb -s 192.168.0.13:39167 install -r app/build/outputs/apk/debug/app-debug.apk`.
  Result: `Success`.
- `dumpsys package com.jesjobom.ararai` after install:
  `versionName=202610091714`, `versionCode=29859674`,
  `lastUpdateTime=2026-10-09 17:22:03` (device local time). Previous version
  was `202610091619`, updated at `2026-10-09 16:26:10`.
- The device reported `mDreamingLockscreen=true` and current focus on
  `NotificationShade`. App launch, new authoring, and rendered card were not
  verified on-device. No existing widget or user data was modified by the
  install.
