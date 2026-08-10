## Why

Normal Chat exposes `Use reasoning` and `Show reasoning`, but currently keeps
both choices only in its ViewModel. Recreating the application resets them even
though the equivalent Voice Chat choice and other Chat presentation settings
are persistent.

## What Changes

- Persist the normal Chat reasoning-request and reasoning-visibility choices in
  the existing Chat preference store.
- Restore each choice when Chat is recreated and the selected model supports
  the corresponding capability.
- Preserve the stored choice while an unsupported or unavailable model is
  selected, while keeping the effective UI controls disabled and off.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `local-llm-hub`: Persist and capability-gate normal Chat reasoning choices.

## Impact

- Affected code: Chat preferences, Chat ViewModel initialization/model changes,
  and focused preference/ViewModel tests.
- Storage: two local booleans are added to the existing `chat_preferences`
  SharedPreferences file; no migration is required and both default to false.
