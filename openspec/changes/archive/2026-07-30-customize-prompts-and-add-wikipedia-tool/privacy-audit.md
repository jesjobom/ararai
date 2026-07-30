# Wikipedia privacy and network audit

## Verdict

The implemented Wikipedia path satisfies the opt-in network contract. Merely
enabling the preference does not execute a request. A request requires an
eligible selected model and a model-emitted structured call. Normal Chat and
Voice Chat share the same guarded engine path.

## Audited controls

- `eligibleKnowledgeToolNames` requires both the persisted user preference and
  explicit model capability metadata.
- `PromptRequest.advertisedToolNames` carries only the normalized eligible set;
  unsupported names are rejected defensively by the engine.
- `WikipediaOpenApiTool` validates the structured arguments, allows one
  attempt per turn, and performs no retry.
- `WikipediaKnowledgeTool` constructs fixed `en`/`pt` official HTTPS
  MediaWiki endpoints. Invalid arguments produce zero transport calls.
- `UrlConnectionKnowledgeHttpTransport` rejects cleartext, redirects, ports,
  queries/fragments on canonical sources, and non-official hosts.
- The Android application disables cleartext traffic and cloud/device-transfer
  backup.
- Only query and language cross the request boundary. Chat history,
  instructions, session IDs, audio, images, model data, and diagnostics are not
  part of the provider request.
- Raw extracts and protocol remain transient. Completed answers atomically
  persist only bounded source provider/title/URL/language/retrieval-time
  metadata; failures and cancellation do not persist partial sources.
- Opening screens/history, restoring a process, direct answers, and model
  changes do not call the provider.

## Evidence

Automated tests cover eligibility, unsupported-tool rejection, zero-network
invalid arguments, fixed hosts, response and size validation, redirects,
timeouts, cancellation, direct answers, one-call/no-retry, protocol leakage,
atomic persistence, restart, and presentation. Deterministic E2B/E4B and live
Wikipedia physical-device evidence is recorded in `spike.md`.

Physical release validation remains required for normal Chat and Voice Chat,
including offline, cancellation, mode/instruction/model switching, and process
recreation. See `docs/device-validation.md`.
