# SM-S901E progressive render contract install

- Change: algorithm authoring receives a short render-capability catalog;
  render authoring receives only the selected capability's contract. The
  typed `collection_card` capability specifies an alias, array field, card
  fields, fallback, and `first` or `seeded_random` selection. The application
  generates and validates the executable source. `custom_source` remains
  available for widgets outside this declarative shape.
- Validation: `scripts/quality-gate.sh` passed on 2026-10-08, including 9/9
  Node runtime-helper tests, JVM tests, Firestore rules tests, QuickJS host
  tests, Spotless, Detekt, Android lint, debug and instrumentation builds,
  release-candidate build and R8 artifact checks, and 11/11 strict OpenSpec
  items. No connected Android instrumentation tests were run.
- Debug APK SHA-256: `36daf3524a0ba71bd64ec075b408bf47f77a470a21c2df34dea0297481906e1e`.
- Device: Samsung SM-S901E, serial `192.168.0.13:39167`.
- Install: `adb install -r` returned `Success`. Android package manager reports
  `versionName=202610072344` and `lastUpdateTime=2026-10-07 23:48:53`
  America/Toronto, advanced from `2026-10-07 22:49:22`.
- Physical authoring with this APK has not yet been rerun. Retrying a session
  with an accepted algorithm checkpoint from the previous APK follows the
  legacy `custom_source` route; regenerate the algorithm stage or start a new
  session to exercise the typed `collection_card` route. Existing widget data
  was preserved by `adb install -r`.
