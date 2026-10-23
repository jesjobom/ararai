# Android launcher widget MVP

## Why

The in-app authoring and managed-widget runtime are the first phase of the product. The intended destination is a live card on the Android launcher. The current specification explicitly excludes launcher widgets, so this change makes that product transition explicit.

## What changes

- Expose an Android launcher widget that can be added from the system widget picker and configured to display one existing managed widget.
- Render a bounded plain-text projection of the validated cached presentation in a fixed, safe Android widget layout. Tapping it opens the widget details in ArarAI.
- Refresh launcher instances from persisted state after authoring confirmation, manual or scheduled execution, enablement changes, and deletion. Reconstruct them after launcher/system update broadcasts without running a model.
- Preserve the last valid cached content after a failed refresh and distinguish empty, disabled, stale, and unavailable states.
- Preserve the first content heading, wrap headings, use the available resized height for body text, show a validated Wikipedia link after the content, and expose a refresh action on the card.

## Out of scope

- Arbitrary model-authored Android layouts or execution in the launcher process.
- Exact-time background updates, interactive tool calls in the launcher, or automatic model inference during refresh.
- Rich presentation parity with the in-app Compose renderer; this first widget uses a compact text projection. When even the resized card cannot show all text, tapping the card opens the full in-app presentation.
