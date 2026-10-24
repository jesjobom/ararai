# Advisory authoring warnings — 2026-10-11

The workflow projection now inspects reviewable/accepted artifacts for two
possible semantic mismatches: random requested with typed `first` selection,
first requested with `seeded_random`, last requested even though the page tool
does not support it, and a day/night request whose call source does not appear
to use `runtime.currentLocalDateTime()`. These are deliberately
heuristic, localized warnings. They neither change the stage status nor remove
Continue, Confirm, or Reprocess actions. Malformed or unrecognized artifacts
produce no warning. Technical shape and frozen typed-selection validation
remain separate.

Validation: targeted projection and temporal-query JVM tests passed. The broad
offline `spotlessApply detekt testDebugUnitTest lintDebug assembleDebug` gate
passed. `spotlessCheck`, `git diff --check`, and strict OpenSpec validation
passed. The `connectedDebugAndroidTest` Gradle task could not start in offline
mode because UTP dependencies were not cached. The already-built app and test
APKs were installed on SM-S901E (`192.168.0.13:39167`), and the focused
Compose stage-card class passed directly through `adb shell am instrument`
after the locked/dozing screen was woken (2 tests). After extending the
selection warning to cover random/randômico, first, and unsupported last,
the targeted JVM tests, detekt, and the broad offline `spotlessCheck detekt
testDebugUnitTest lintDebug assembleDebug` gate passed again. Strict OpenSpec
validation and `git diff --check` passed. Debug APK `202610102354` was then
installed and verified on SM-S901E, advanced from `202610101912`.

No new widget authoring run was started; runtime warning presentation for a
real newly generated candidate remains for JJ's next physical retest.
