## ADDED Requirements

### Requirement: Text Chat shows local message creation time

The text Chat SHALL show the persisted creation date and time beside the sender
name for each message, formatted with the device's current locale and local time
zone. The date and time SHALL use the unambiguous `yyyy-MM-dd HH:mm` format,
independent of locale. The presentation SHALL be independent of whether the
message originated in text Chat or Voice Chat.

#### Scenario: Text-originated conversation is displayed

- **WHEN** the user views a persisted text-chat message
- **THEN** its header shows `You` or `ArarAI` together with its local creation
  date and time in `yyyy-MM-dd HH:mm` format

#### Scenario: Voice-originated conversation is displayed in text Chat

- **WHEN** the user opens in text Chat a conversation containing messages
  created through Voice Chat
- **THEN** those messages show their persisted creation date and time using the
  same header presentation
