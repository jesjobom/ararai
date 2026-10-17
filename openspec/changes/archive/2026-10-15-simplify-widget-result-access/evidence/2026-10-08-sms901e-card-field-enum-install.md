# SM-S901E card item-field selection failure and fix

- Read-only inspection of the app-private authoring database found a new
  session created `2026-10-08 04:11:15` UTC. Feasibility, algorithm,
  call-function, and plan stages succeeded; the algorithm selected
  `collection_card`.
- Three render captures completed between `04:17:02` and `04:20:30` UTC, all
  rejected as `InvalidPresentationFields`. The captures used literal card
  headings or the user's instruction sentence as values for `title` and
  `body`, where the contract requires exact property names on an array item.
  They selected `seeded_random` correctly. No Wikipedia request occurred in
  this local validation failure.
- Change: the card capture schema now enumerates the selected tool-result
  aliases, array paths, and string item-property names. The model-facing
  context and repair message state that these are property names and only
  `fallback` is literal display text. A test checks nested generic paths and
  another rejects a captured-style literal title.
- Validation: targeted pipeline and parser tests passed. The broader
  `spotlessCheck detekt testDebugUnitTest lintDebug assembleDebug` gate passed.
  Strict OpenSpec validation passed. No connected Android instrumentation
  tests were run.
- Debug APK SHA-256: `3a49afec0b4cff3944b094b526ceaa3eee2f8895791a57a17308e5215155571f`.
- Device: Samsung SM-S901E, serial `192.168.0.13:39167`.
  `adb install -r` returned `Success`; Android reports
  `versionName=202610080024` and `lastUpdateTime=2026-10-08 00:26:31`
  America/Toronto, advanced from `2026-10-08 00:09:30`.
- Physical render with the corrected card-field schema has not yet been
  rerun. The existing session's selected `collection_card` algorithm remains
  usable for a retry.
