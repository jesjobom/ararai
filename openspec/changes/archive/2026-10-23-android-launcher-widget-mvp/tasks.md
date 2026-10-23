## Implementation

- [x] Amend the in-app-only specification and add the launcher-widget MVP contract.
- [x] Add provider metadata, a fixed RemoteViews layout, configuration selection, and appWidgetId bindings.
- [x] Project validated cached presentations and status into a safe launcher card with detail navigation.
- [x] Notify launcher instances after saved run and definition changes, and recover on system widget updates.
- [x] Add focused projection, binding, and update-flow tests.
- [x] Run the full quality gate and non-destructive device registration/install checks.

## Launcher feedback

- [x] Separate a wrapped headline, flexible body, and validated link in the launcher snapshot and layout.
- [x] Add a bottom-right refresh control that coalesces one-time execution and respects enablement.
- [x] Cover projection, link policy, and refresh dispatch with focused tests.
- [x] Run the full quality gate, install over the retained device app, and confirm existing launcher instances remain registered.
- [x] Confirm the launcher appearance and exercise its link and refresh actions on device (verified by JJ on 2026-10-23).
