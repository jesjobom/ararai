# Change: Download models in the background

## Why

Model downloads can be large and currently run only as ordinary in-process
coroutines. Android may stop the process after the app leaves the foreground,
causing users to lose download progress and restart the transfer.

## What Changes

- Run model transfers under a foreground service with a persistent progress
  notification.
- Let users cancel the active model download from the notification or UI.
- Keep download state observable when the activity is recreated.
- Preserve valid partial files and resume HTTP downloads when supported.
- Request notification permission contextually on Android versions that require
  it, while retaining Android's foreground-service behavior if permission is
  denied.

## Impact

- Affected spec: `local-llm-hub`
- Affected code: application lifetime, model controller/downloader, Android
  service/manifest, notification permission, tests and documentation
- Inference and Chat generation lifecycle: unchanged
