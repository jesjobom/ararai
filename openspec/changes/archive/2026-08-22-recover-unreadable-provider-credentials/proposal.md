## Why

An encrypted web-provider credential can become unreadable after Keystore key
invalidation or ciphertext corruption. Preference-key presence currently still
classifies that provider as configured and potentially enabled while reads
return no token, causing persistent authentication failures with no explicit
recovery state.

## What Changes

- Represent missing and unreadable encrypted credentials as distinct internal
  states without exposing token material.
- Apply a deliberate recovery policy that immediately disables an unusable
  provider, retains the unreadable ciphertext until explicit replacement or
  removal, and prompts the user to save a replacement credential.
- Add instrumentation coverage for malformed ciphertext and invalidated-key
  behavior as far as the Android test environment can reproduce it.

## Capabilities

### Modified Capabilities

- `local-llm-hub`: External knowledge providers cannot remain effectively
  enabled with an unreadable encrypted credential.

## Impact

- Affected code: encrypted credential storage, provider preferences/UI, provider
  gating, and Android instrumentation tests.
- Privacy: token values remain encrypted and must never be logged.
