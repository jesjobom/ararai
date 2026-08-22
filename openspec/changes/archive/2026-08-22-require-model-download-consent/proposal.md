## Why

ArarAI currently starts downloading its default chat model whenever no local
chat model is available. Because model artifacts are large, beginning that
transfer without consent can unexpectedly consume mobile data on first launch.

## What Changes

- Stop automatically downloading the default chat model during application
  startup.
- On the first application launch without a local chat model, explain that a
  model is required and offer actions to download the named default model,
  including its approximate size, open Model Management, or close the dialog.
- Persist dismissal/completion of this first-launch prompt so it does not recur
  on later launches; retain discoverable download guidance while no local chat
  model exists.
- Start the default-model download immediately, on the current network, only
  after the user chooses the download action.
- Keep normal Chat accessible for viewing and managing conversation history
  without a model, but block message submission and show brief download
  guidance when the user interacts with the unavailable composer.
- Present Voice Chat as unavailable while no local chat model exists, using a
  disabled visual treatment and brief download guidance when tapped.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `local-llm-hub`: require explicit consent before the initial model download
  and define model-unavailable behavior for Home and normal Chat.
- `voice-chat`: define Voice Chat entry behavior when no local chat model is
  available.

## Impact

- Affected code: model-catalog startup policy, a persisted first-launch prompt
  preference, Home navigation cards, normal Chat composer feedback, localized
  strings, and Compose/domain tests.
- Download transport, integrity validation, foreground progress, cancellation,
  and explicit downloads from Model Management remain unchanged.
- Privacy/networking: model networking no longer begins without an explicit user
  action; choosing download permits the existing transfer on the current
  network, including mobile data.
