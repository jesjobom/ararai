# Design Notes

## UI Direction

The home should feel like a small operational hub, not a landing page. It should
show the app identity, a compact status summary, and one clear action:
`Model status`.

Keep the layout simple:

- app title
- short product subtitle
- model status summary
- one button/card to open model status

The model status screen should show:

- configured model name
- current download/model state
- progress percent when available
- progress byte detail when available
- retry action only on failed state
- back action to home

## Navigation

Do not add a navigation dependency for two screens. A small sealed destination
or enum in Compose state is enough for this slice.

## Testing

Test UI-state mapping in JVM tests. Full Compose UI tests can wait until the UI
surface becomes more complex.
