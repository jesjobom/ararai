# Change: Standardize Chat settings actions

## Why

Chat and Voice Chat expose inconsistent settings contracts. Voice Chat stages
changes behind Save/Cancel while Chat applies changes immediately and only offers
Close. Users should not need to remember different persistence behavior for two
closely related configuration dialogs.

## What Changes

- Persist every Chat and Voice Chat setting as soon as it changes.
- Give both dialogs a right-aligned Close action that only dismisses the dialog.
- Give both dialogs a left-aligned Reset action that restores and immediately
  persists every default value without dismissing the dialog.
- Cover automatic persistence, reset, and dismissal with Compose tests.

## Impact

- Affected specs: `local-llm-hub`, `voice-chat`
- Affected code: Chat and Voice Chat settings dialogs and UI tests
- Data migration: none
