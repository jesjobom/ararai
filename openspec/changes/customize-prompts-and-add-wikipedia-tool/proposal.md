## Why

ArarAI currently applies one checked-in system prompt to both normal Chat and
Voice Chat. Users cannot tune the two interaction styles independently, and the
local model cannot obtain external knowledge during a conversation even when a
supported runtime can request application-owned tools.

Normal Chat benefits from detailed written answers, while Voice Chat needs
shorter, speech-friendly behavior without requiring explicit buttons or command
phrases. The Gemma 4 LiteRT-LM models already selected by the app are the
narrowest practical first target for automatic tool calling because the
LiteRT-LM API exposes tool definitions, structured calls, tool responses, and
automatic execution.

## What Changes

- Add an `Instructions and tools` destination where the user can maintain
  separate normal-Chat and Voice-Chat instructions, restore each checked-in
  default, and enable or disable supported tools.
- Compose each effective system instruction from an app-owned invariant section
  and the selected interaction mode's user-editable section.
- Persist instruction and tool preferences locally without splitting the shared
  conversation history.
- Invalidate incompatible retained native conversation state when an effective
  system instruction or enabled-tool set changes.
- Add an optional `wikipedia_search` knowledge tool for the checked-in Gemma 4
  LiteRT-LM models.
- Register the Wikipedia tool through LiteRT-LM structured automatic tool
  calling. The model decides during an ordinary text or voice turn whether the
  tool is needed; the user does not issue a command or activate a per-turn
  search control.
- Query an official Wikipedia/MediaWiki API, return bounded untrusted
  encyclopedic context to the model, and attach human-readable source metadata
  to the completed answer.
- Keep the tool unavailable for unsupported models and runtimes rather than
  introducing a text-command fallback in this increment.
- Bound calls, result size, timeout, and failure behavior so a network or tool
  failure cannot loop indefinitely, fabricate a successful search, or block a
  Voice Chat session permanently.

This change is deliberately limited to editable interaction instructions and
one Gemma 4/LiteRT-LM knowledge tool. Generic web search, news, arbitrary user
skills, downloadable tool code, future alternate-runtime tool calling, a text-protocol
fallback, multi-tool planning, and background tool execution are deferred.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `local-llm-hub`: Add mode-specific user instructions, local tool preferences,
  Gemma 4/LiteRT-LM automatic Wikipedia tool calling, bounded external context,
  and source presentation.
- `voice-chat`: Use Voice-Chat-specific instructions and execute eligible
  Wikipedia calls automatically inside the uninterrupted half-duplex loop.

## Impact

- Settings/navigation UI: a new instructions-and-tools destination, editors,
  restore-default actions, tool status, compatibility explanation, and network
  disclosure.
- Preferences: persisted normal-Chat instruction, Voice-Chat instruction, and
  Wikipedia enabled state.
- Conversation coordination: effective instruction selection by turn origin,
  compatibility invalidation, tool progress, bounded execution, and source
  metadata.
- LiteRT-LM boundary: tool registration, automatic tool calling, tool-result
  handling, cancellation, and loop limits for compatible Gemma 4 bundles.
- Network: application-owned requests to an official Wikipedia/MediaWiki API;
  local inference, conversation storage, and media remain app-owned.
- Persistence: completed assistant messages may include bounded source metadata;
  raw retrieved page content is not durable conversation history.
- Tests and validation: preference restoration, cross-screen prompt selection,
  runtime invalidation, supported/unsupported models, multilingual queries,
  automatic voice flow, network failures, cancellation, result limits, source
  rendering, and physical-device tool-calling behavior.
