# Refine managed widget details

## Why

The details screen mixes primary and secondary actions, repeats enablement status, and lets execution history and source code dominate the page. The current layout makes the latest result harder to act on.

## What changes

- Place the enablement switch and an overflow menu for edit, duplicate, and delete at the top.
- Put Refresh now immediately after the saved result and timestamp.
- Show the active revision beside the enablement switch and schedule in its own field, with inexact Android scheduling explained by an information tooltip.
- Initially show only the four most recent runs, with an option to expand or collapse the full retained history.
- Render JavaScript in a distinct, selectable code surface with basic syntax highlighting and automatic line wrapping while preserving the stored source text.
