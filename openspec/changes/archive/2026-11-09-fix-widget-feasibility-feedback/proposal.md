# Change: Clarify terminal widget feasibility results

## Why

A valid feasibility artifact can conclude that a widget is unachievable or needs
clarification. The staged-authoring screen currently calls that attempt
"Succeeded" and leaves Advance available, even though no next stage exists.

## What Changes

- Show the terminal feasibility outcome and its bounded reason or question in the
  stage card, without requiring the user to expand captured JSON.
- Do not offer Advance for a terminal feasibility result; keep reprocess and
  discard/start-new-request available.
- Preserve the successful capture as an inspectable attempt and the saved
  session for resumption.

## Impact

- Affected spec: `llm-widget-authoring`
- Affected code: staged workflow projection and screen, localized strings, tests
