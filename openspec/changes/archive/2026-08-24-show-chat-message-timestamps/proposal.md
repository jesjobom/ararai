# Change: Show local timestamps on text-chat messages

## Why

The text Chat identifies each message sender but does not show when the message
was created. This is especially confusing when a conversation began in Voice
Chat and is reviewed later in the text interface.

## What Changes

- Preserve the persisted message creation timestamp in the UI projection.
- Show the local date and time beside `You` or `ArarAI` on every persisted text
  Chat message.
- Apply the same presentation to messages created through Voice Chat.

## Impact

- Affected spec: `local-llm-hub`
- Affected code: Chat message UI projection, message header, and formatter tests
- Persistence: no schema migration; the existing `created_at_millis` value is used
