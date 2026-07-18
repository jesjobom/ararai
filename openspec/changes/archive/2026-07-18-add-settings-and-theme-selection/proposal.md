# Change: Add application settings and theme selection

## Why

ArarAI already provides light and dark Material color schemes, but always follows
the Android system appearance and has no general application settings screen.
Users need an explicit appearance preference, and the app needs an extensible
destination for future application-level options.

## What Changes

- Add a Settings destination reachable from Home.
- Organize Settings into sections, beginning with Appearance.
- Let the user select System, Light, or Dark theme behavior.
- Persist the selected behavior locally and restore it on later launches.
- Apply changes immediately to the whole application while retaining Material
  dynamic colors on supported Android versions.

## Impact

- Affected spec: `local-llm-hub`
- Affected code: application startup, local preferences, top-level navigation,
  Compose theme and Settings presentation
- Inference, model files, Chat history, privacy, and networking: unchanged
