## Baseline inventory

Before extraction, `ArarAiApp.kt` contained 2,018 lines and owned:

- application navigation across Home, Chat, Voice Chat, Diagnostics, Models,
  Whisper benchmark, Settings, licenses, and assistant configuration;
- application-scoped controller composition and lifecycle callbacks;
- reporting state and callbacks;
- the shared application scaffold;
- complete Home, Settings, assistant-configuration, diagnostics, and model
  management destinations plus their presentation helpers.

The application shell remains responsible for destination state, back handling,
controller ownership, external model-management requests, and wiring destination
callbacks. Existing Chat, Voice Chat, Whisper benchmark, and license screens were
already separate files.

## First extraction slice

Home was selected because it is a stateless presentation destination with the
smallest cohesive boundary: `ModelStatusUiState`, the version label, and five
navigation callbacks. Its only shared presentation dependency is the existing
capability-tag renderer.

`HomeScreen`, its brand header, conversation card, and status card now live in
`HomeScreen.kt`. Navigation ownership and every callback remain in
`ArarAiApp.kt`; routes, controller lifecycles, localization, accessibility
semantics, and visual values are unchanged.

The pre-existing Chat-from-Home journey remains as characterization evidence.
`homeDestinationPreservesEveryNavigationCallback` adds explicit coverage for all
five Home navigation boundaries.

## Result

After the first slice, `ArarAiApp.kt` contains 1,827 lines, a reduction of 191
lines (9.5%). No destination beyond Home was bundled into this reviewable slice.
The remaining application-shell complexity suppressions are retained because
the shell still owns navigation and controller/reporting orchestration.
