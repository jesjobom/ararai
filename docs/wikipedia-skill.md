# Wikipedia skill: privacy, networking, and validation

## User-visible contract

Wikipedia retrieval is optional and disabled by default. The user enables it
under **Instructions and tools**. The preference is preserved across model
changes. Checked-in Chat models explicitly advertise `wikipedia_pages` and
`wikipedia_on_this_day`; both are also available to confirmed widgets. The
currently configured bundles are Gemma 4 E2B and E4B on LiteRT-LM 0.14.0. The
legacy model-only `wikipedia_search` binding remains registered for
compatibility but is not advertised by those catalog entries.

The model, not an application keyword parser, decides whether a normal prompt
needs research. That decision is probabilistic. The application retains
deterministic authority over eligibility, arguments, endpoints, limits,
cancellation, storage, and presentation.

## Data flow and network boundary

An official Wikipedia request occurs only after this complete chain:

1. the user preference is enabled;
2. the selected model advertises the capability;
3. the model emits a structured `wikipedia_pages` or
   `wikipedia_on_this_day` call;
4. the adapter accepts either bounded `query`/`language` page arguments or
   numeric `month`/`day` plus `language` event arguments;
5. the provider constructs the fixed official endpoint.

The request contains only the model-selected query or calendar month/day and a
validated two- or three-letter language code. It does
not contain conversation history, the system instruction, a session ID, audio,
images, local model content, or application diagnostics.

Only `https://<language>.wikipedia.org/w/api.php` is accepted for pages and
`https://<language>.wikipedia.org/api/rest_v1/feed/onthisday/events/MM/DD` for
historical events. Cleartext traffic is disabled application-wide. Redirects,
other feed types/paths, impossible calendar dates, and non-official canonical
source URLs are rejected. The providers enforce connection/read/total
deadlines, strict UTF-8 and JSON validation, wire/decoded/result bounds,
cooperative cancellation, and one shared three-call ceiling per model turn.

Tool descriptions and the system instruction reserve `wikipedia_on_this_day`
for historical events on an explicit month/day, including “today in history”.
They reserve `wikipedia_pages` for people, places, concepts, notable works,
dates themselves, and other direct stable page lookups. Current news, changing
facts, comparisons, recommendations, broad research, and multi-source evidence
remain web-search work when available.

Model downloads are a separate, user-initiated network path to catalog
artifact URLs. The opt-in live diagnostic is also separate: it discloses its
fixed synthetic queries before execution and is not an acceptance gate.

## Persistence and presentation

During a turn, research progress and retrieved sources are transient state.
Sources are committed only with a completed assistant answer. A failed or
cancelled turn cannot persist partial source metadata.

Persisted model-source fields are limited to provider, title, canonical URL,
language, and retrieval timestamp. At most three source records are attached to
a completed model answer. Raw extracts, MediaWiki/feed JSON, tool
arguments/results, and LiteRT-LM
protocol tokens are neither persisted nor shown.

Normal Chat renders canonical links with the associated answer. Voice Chat
does not speak intermediate tool events or source metadata; it speaks only the
final model answer. Both modes share the same persisted conversation, so a
Voice answer and its sources remain visible in normal Chat after mode changes
or process recreation.

## Automated evidence

The checked-in tests cover:

- disabled preference, unsupported model, and empty capability resolution;
- distinct page/event schemas, dual model/widget eligibility, selection
  instructions, and the shared model-turn budget;
- registration only for an advertised capability and defensive engine
  rejection of unsupported tools;
- invalid arguments producing zero transport calls;
- fixed HTTPS hosts and paths, impossible-date rejection, rejected
  redirects/non-official URLs, strict response
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
