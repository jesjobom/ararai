# Improve widget review and editing

## Why

The confirmation preview shows generated JavaScript as plain text even though the saved widget detail highlights and wraps it. Editing a saved widget also starts with an empty instruction field despite retaining the active revision's original prompt.

## What Changes

- Use the same selectable, syntax-highlighted JavaScript source component in draft confirmation and saved-widget detail.
- Start editing with the active revision's stored prompt in the editable instruction field. Legacy revisions without a stored prompt remain blank.
- Preserve the existing confirmation and revision behavior; entering edit does not itself generate or save a new revision.

## Capabilities

### Modified Capabilities

- `managed-widgets`: Make source review consistent and prefill edit instructions from the active revision.

## Impact

Compose widget detail, authoring navigation and preview. No schema, network, or widget runtime changes.
