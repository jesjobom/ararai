# Select widget refresh frequency in a dedicated stage

## Why

The feasibility stage may choose a supported but incorrect 24-hour default for a request to refresh four times per day. Its current decision also includes tool selection, feasibility, and title, making frequency a secondary concern.

## What Changes

- Add a separate, automatically advanced schedule stage after achievable feasibility and before algorithm generation for new authoring sessions.
- Offer the model only 1, 2, 4, 6, 12, and 24 hours, plus no periodic schedule. It must choose the closest offered cadence, not ask for clarification about frequency. New feasibility requests do not receive the schedule-option list.
- Make the accepted schedule artifact authoritative for the draft; new sessions omit the feasibility schedule field entirely, while older sessions retain their original wire contract for resumability.
- Let a user configure every whole-hour interval from 1 through 24 in a numeric field on an existing widget's detail screen, without another model generation; persist consent and reconcile its periodic work. Older saved 48-, 72-, and 168-hour schedules remain executable but are no longer offered for new choices.
- A completed manual refresh restarts the periodic cycle with its next eligible execution roughly one interval later, without promising an exact wall-clock time.

## Impact

Authoring stage graph, validation, schema, previews, widget-detail controls, periodic scheduling policy, documentation, and Android build/test validation. Existing schema-version-1 sessions keep their original path.
