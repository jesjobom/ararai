# Change: Simplify Chat Session Dialog

## Why

The session dialog has limited horizontal space and currently exposes too many
labeled actions. Renaming is tied to the selected session rather than the
session item the user is acting on.

## What Changes

- Rename any session by pressing and holding its session card.
- Remove the bottom `Rename` action.
- Present `New` beside the `Chat sessions` title.
- Keep only `Clear all` and `Close` in the bottom action row, with icons.
- Indicate the active session only through its existing differentiated card
  color, without a `Current` text label.
- Preserve tap-to-select and per-session deletion.

## Impact

- Touches the Chat session dialog interaction and session rename ViewModel API.
- Does not change session persistence or destructive-action confirmation.
