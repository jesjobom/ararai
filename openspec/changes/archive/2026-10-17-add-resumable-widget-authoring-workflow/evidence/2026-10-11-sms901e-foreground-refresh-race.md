# SM-S901E foreground-refresh crash follow-up

The installed APK `202610102354` closed repeatedly during staged widget
authoring. On the device (America/Toronto), Android logged an allowed
`REFRESH_WIDGET_AUTHORING` foreground-service start at 21:32:10.408, service
removal while still waiting for promotion at 21:32:10.438, and
`ForegroundServiceDidNotStartInTimeException` at 21:32:10.499. The same
sequence repeated at 21:32:26. This exception also appears in older device
logs, including 2026-10-03 evidence, so the latest warning UI change did not
introduce the underlying service race. It may have made the sequence more
visible; that cannot be established from the available logs.

Cause: the notification presenters called `stopService()` on a terminal
state, which can overtake a queued `startForegroundService()` request. The
service itself had an explicit refresh action but could not acknowledge a
request Android had already removed. Both presenter paths now issue a final
refresh; the service promotes itself before reading the mutable session and
then detaches/stops according to the latest state.

Sanitized log: `2026-10-11-sms901e-foreground-refresh-race.log`.
SHA-256: `60f75b7b01e07b90c21e187f8e03cc185e4d0fdbc4476d567a32270093d54832`.

Two Robolectric regressions verified rapid active-to-terminal transitions
for the resumable and legacy presenters without any direct stop request.
`spotlessCheck detekt testDebugUnitTest lintDebug assembleDebug` passed, as did
strict OpenSpec validation and `git diff --check`. Debug APK SHA-256:
`9e33b72e7fd25005bf2310194bf271c370937a9eb779d0a668f31b7971b2c7bc`.
`adb install -r` reported success; the device package advanced to version
`202610112137` with `lastUpdateTime=2026-10-11 21:40:39` local time. No
model-driven authoring run was performed after installation; a fresh physical
staged run is still needed to confirm the crash no longer reproduces.
