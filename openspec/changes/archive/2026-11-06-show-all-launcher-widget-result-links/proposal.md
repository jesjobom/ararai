# Show each saved result link on Android Home

## Why

A managed widget can save up to ten search-result links and show all of them in its detail view, but the Android Home projection currently makes only the first result clickable. The other titles are plain text, so the compact view suggests less functionality than the saved result provides.

## What Changes

- Render saved multi-link presentations as a scrollable list in the Android launcher card, preserving presentation order and one clickable row per validated link.
- Keep the current compact heading/body/single-link layout for other presentations.
- Open a row only when its destination still belongs to the bound widget's validated saved presentation.
- Continue rendering solely from local saved data; no model or network work runs while drawing the card.

## Impact

Launcher projection, RemoteViews collection rendering, link handling, tests, and widget documentation. Existing bound launcher instances and stored widget data need no migration.
