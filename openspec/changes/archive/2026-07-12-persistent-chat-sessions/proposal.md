# Change: Persistent Chat Sessions

## Why

ArarAI's Chat should become useful for daily work, not just one-off prompt
tests. The app currently keeps messages only in memory and reverts the selected
model to the catalog default on restart. Users need persistent chat sessions,
free switching between them, deletion, and context reuse from conversation
history. The system prompt should also be configurable from the checked-in app
configuration so prompt behavior can evolve without changing code.

## What Changes

- Persist the selected model ID locally and restore it on app startup when still
  present in the checked-in catalog.
- Add persistent chat sessions with locally stored messages.
- Allow users to create, switch, rename, and delete sessions from the Chat UI.
- Build generation prompts from the configured system prompt plus recent
  session history within a simple per-model context budget.
- Keep the initial context strategy simple: include the newest messages that fit
  a conservative character-based estimate and do not summarize yet.

## Impact

- Adds local persistence for app preferences and chat history.
- Touches model catalog state, chat state/view model, chat UI, and model
  configuration parsing.
- Does not change native runtime APIs or model download behavior.
