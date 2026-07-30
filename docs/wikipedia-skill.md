# Wikipedia skill: privacy, networking, and validation

## User-visible contract

Wikipedia retrieval is optional and disabled by default. The user enables it
under **Instructions and tools**. The preference is preserved across model
changes, but the tool is registered only for an installed selected model whose
catalog entry explicitly advertises `wikipedia_search`. The currently validated
bundles are Gemma 4 E2B and E4B on LiteRT-LM 0.14.0.

The model, not an application keyword parser, decides whether a normal prompt
needs research. That decision is probabilistic. The application retains
deterministic authority over eligibility, arguments, endpoints, limits,
cancellation, storage, and presentation.

## Data flow and network boundary

An official Wikipedia request occurs only after this complete chain:

1. the user preference is enabled;
2. the selected model advertises the capability;
3. the model emits a structured `wikipedia_search` call;
4. the adapter accepts a bounded `query` and `language`;
5. the provider constructs the fixed official endpoint.

The request contains the model-selected query and either `en` or `pt`. It does
not contain conversation history, the system instruction, a session ID, audio,
images, local model content, or application diagnostics.

Only `https://<language>.wikipedia.org/w/api.php` is accepted, where `language`
is a validated two- or three-letter lowercase ISO code. Cleartext traffic is
disabled application-wide. Redirects and non-official canonical source URLs
are rejected. The provider enforces connection/read/total deadlines, strict
UTF-8 and JSON validation, wire/decoded/context bounds, cooperative
cancellation, a three-call ceiling per turn, English-first search, and an
optional retry in the automatically detected question language when its
Wikipedia edition exists.

Model downloads are a separate, user-initiated network path to catalog
artifact URLs. The opt-in live diagnostic is also separate: it discloses its
fixed synthetic queries before execution and is not an acceptance gate.

## Persistence and presentation

During a turn, research progress and retrieved sources are transient state.
Sources are committed only with a completed assistant answer. A failed or
cancelled turn cannot persist partial source metadata.

Persisted source fields are limited to provider, title, canonical URL,
language, and retrieval timestamp. At most three sources cross the provider
boundary. Raw extracts, MediaWiki JSON, tool arguments/results, and LiteRT-LM
protocol tokens are neither persisted nor shown.

Normal Chat renders canonical links with the associated answer. Voice Chat
does not speak intermediate tool events or source metadata; it speaks only the
final model answer. Both modes share the same persisted conversation, so a
Voice answer and its sources remain visible in normal Chat after mode changes
or process recreation.

## Automated evidence

The checked-in tests cover:

- disabled preference, unsupported model, and empty capability resolution;
- registration only for an advertised capability and defensive engine
  rejection of unsupported tools;
- invalid arguments producing zero transport calls;
- fixed HTTPS hosts, rejected redirects/non-official URLs, strict response
  validation, bounded content, timeouts, and cancellation;
- direct answers, successful calls, controlled failures, one-call/no-retry,
  protocol leakage, and retained-conversation invalidation;
- atomic answer/source persistence, SQLite restart round-trip, transient
  progress cleanup, and Compose source presentation.

The deterministic E2B/E4B characterization and live API evidence are recorded
in the active OpenSpec change. Physical Chat and Voice behavior remains a
release/device gate; follow `docs/device-validation.md`.

## Future skills

Conversation compatibility and turn settings use a normalized set of
advertised tool names rather than a Wikipedia boolean. A future skill must
still define its own explicit catalog capability, opt-in/privacy disclosure,
validated adapter, bounded provider, lifecycle events, persistence policy, and
network audit. Adding a tool name must invalidate an incompatible retained
native conversation; it must not silently broaden this Wikipedia contract.
