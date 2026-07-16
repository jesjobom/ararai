# Change: Clear All Chat Sessions

## Why

The Chat session list supports deleting individual sessions, but cleaning up a
large history requires repeating that action for every session. Chat needs one
explicit bulk action that removes the complete local conversation history
without making destructive behavior easy to trigger accidentally.

## What Changes

- Add a `Clear all` action to the Chat session list.
- Require explicit confirmation before deleting any data.
- Delete all stored chat sessions and their messages atomically.
- Create and select one new empty session after the deletion succeeds so Chat
  remains in a valid usable state.
- Clear any draft text and pending image or audio attachments when the history
  is cleared.
- Prevent bulk deletion while generation is active.

## Impact

- Touches the Chat session list UI, ChatViewModel session state, session-store
  contract and implementations, and focused persistence/view-model tests.
- Permanently deletes only local chat sessions and their messages; downloaded
  models and application settings are unaffected.
