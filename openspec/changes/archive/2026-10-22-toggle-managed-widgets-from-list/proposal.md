# Toggle managed widgets from the list

## Why

The Widgets list shows an enabled/disabled label, but changing that state
requires opening each widget's details. The list can expose the existing
enablement operation directly.

## What changes

- Replace the list's enablement text with an accessible switch for each widget.
- Reuse the existing enablement and schedule reconciliation path so disabling
  cancels future work and enabling restores the confirmed schedule.
- Keep opening a widget's details separate from toggling its switch.
- Show an in-progress state and a local error if changing enablement fails.
