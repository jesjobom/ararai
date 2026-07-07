# Configure Generation Token Limit

## Why

Real local generation is working, but responses can be cut off because the
runtime currently uses a hardcoded generation limit of 128 tokens. That value is
too small for useful chat answers and cannot be tuned per configured model.

The limit should live with the checked-in model inference defaults so it can be
adjusted intentionally without changing engine code.

## What Changes

- Add `inference.maxTokens` to the fixed model configuration.
- Parse and validate the maximum generated-token count as part of
  `InferenceConfig`.
- Pass the configured value to the real local LLM engine.
- Increase the configured default from the current hardcoded 128-token behavior
  to a more useful initial value.

## Out Of Scope

- User-facing generation settings.
- Timeout controls.
- Model replacement.
- Conversation-history context budgeting.
