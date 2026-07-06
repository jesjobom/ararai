# Show Model Download Progress

## Why

The app can now download the configured model safely, but first-run download can
take long enough that a static "downloading" message looks stuck. The next small
improvement is to surface download progress from the downloader through startup
state into the debug UI.

## What Changes

- Report downloaded bytes while streaming the configured model file.
- Include the configured expected byte count as total progress when available.
- Update startup state with progress while the model is downloading.
- Render simple progress text in the debug UI.
- Add tests for downloader progress callbacks and ViewModel progress formatting.

## Out Of Scope

- Pause/resume.
- Background-only download behavior.
- WorkManager migration.
- Download speed or ETA calculation.
- Native llama.cpp/JNI inference.
- Multiple models or model selection.
