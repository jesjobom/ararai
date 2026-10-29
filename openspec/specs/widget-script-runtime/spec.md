# widget-script-runtime Specification

## Purpose
Define how ArarAI validates and executes untrusted JavaScript widget programs
through explicit capabilities, deterministic inputs, bounded tool plans, and
constrained presentation output without granting ambient application access.

## Requirements

### Requirement: Versioned widget program package

The application SHALL accept a widget program only as a strict package whose
JSON manifest declares a supported manifest schema version, script language and
API version, entrypoints, requested capabilities, requested resource limits,
and the SHA-256 digest of its separately stored UTF-8 source. The application
SHALL reject unsupported versions, missing or additional manifest fields,
malformed or oversized content, invalid identifiers, and source whose digest
does not match before evaluating any code.

#### Scenario: Accept a supported intact package

- **WHEN** a package has a valid supported manifest and its bounded source
  matches the declared digest
- **THEN** the application produces a normalized immutable program package
- **AND** does not evaluate the source during package validation.

#### Scenario: Reject an incompatible or altered package

- **WHEN** a package uses an unsupported schema, language, or API version or
  its source digest does not match
- **THEN** validation returns a controlled incompatible-or-invalid-program
  failure
- **AND** no JavaScript context or tool invocation is created.

### Requirement: Explicit capability grant

Each execution SHALL receive an application-owned grant no broader than the
capabilities declared by the program and the application's fixed policy. A
program MUST NOT access an undeclared, unavailable, or ungranted tool, runtime
value, history scope, helper, action, or presentation component, and manifest
resource values MUST NOT raise application hard limits.

#### Scenario: Use a declared and granted capability

- **GIVEN** a program declares a capability supported by its API version
- **AND** the application grants that capability for the current execution
- **WHEN** the program uses the capability within its bounds
- **THEN** the runtime makes only that documented capability available.

#### Scenario: Reject capability escalation

- **WHEN** a program requests or uses an undeclared, unsupported, or ungranted
  capability or requests a limit above application policy
- **THEN** execution fails with a controlled capability-or-policy failure
- **AND** the attempted operation has no external effect.

### Requirement: Isolated JavaScript environment

Widget source SHALL execute as untrusted code in a fresh isolated environment
that exposes only the versioned widget API and immutable structured inputs. It
MUST NOT expose Android or JVM objects, reflection, class loading, credentials,
environment or system properties, filesystem, processes, arbitrary network
access, dynamic imports, WebAssembly, dynamic code evaluation, or application
singletons.

#### Scenario: Execute ordinary program logic

- **WHEN** a valid program uses supported declarations, functions,
  assignments, conditionals, collection operations, or loops
- **THEN** the runtime evaluates that logic using only its structured inputs and
  granted widget API.

#### Scenario: Attempt ambient access

- **WHEN** source attempts to reach a forbidden global, host object, import,
  dynamic evaluator, filesystem, process, credential, or direct network API
- **THEN** the operation is unavailable or execution fails in a controlled way
- **AND** no forbidden resource is read, changed, or contacted.

### Requirement: Deterministic runtime context

Time, timezone, locale, and optional pseudo-randomness SHALL be supplied
explicitly as immutable execution inputs through a versioned deterministic
runtime standard library. Kotlin SHALL construct one fresh snapshot for each
manual, background, preview, and authoring dry-run execution. A helper such as
`runtime.currentLocalDateTime()` SHALL return values from that snapshot and
MUST NOT read a QuickJS or host wall clock. Hidden wall-clock access, including
ambient `Date.now()`, and ambient randomness MUST NOT be available; a replay
with the same program, grant, context, seed, tool results, and history input
SHALL produce the same tool plan and presentation result.

#### Scenario: Resolve the current local date

- **WHEN** a program needs the current local date during a widget execution
- **THEN** it obtains year, month, day, time, timezone, and language as granted
  immutable values from the execution's runtime snapshot
- **AND** does not use an authoring-time constant or read a separate runtime
  clock.

#### Scenario: Keep one execution internally consistent

- **WHEN** generated code asks the deterministic clock helper more than once in
  the same plan or presentation execution
- **THEN** every call returns the same immutable snapshot
- **AND** validation, preview, retry, and execution cannot cross a clock tick
  within that execution.

#### Scenario: Refresh time for a later widget run

- **WHEN** the same confirmed program starts a later manual or background run
- **THEN** the application supplies a newly constructed local date/time snapshot
- **AND** the program can derive the new date without model inference or source
  changes.

#### Scenario: Replay a seeded selection

- **WHEN** the same program execution is replayed with identical inputs and
  pseudo-random seed
- **THEN** every supported pseudo-random choice produces the same result.

#### Scenario: Attempt ambient clock access

- **WHEN** source tries to call `Date.now()`, construct an ambient current date,
  access a host/JVM clock, or use unseeded randomness
- **THEN** the operation is unavailable or fails with a controlled runtime
  result
- **AND** no hidden environmental value enters the plan or presentation.

### Requirement: Bounded two-phase program execution

The runtime SHALL execute planning and presentation as separate bounded phases.
The planning entrypoint SHALL return only a finite structured list of aliased
tool requests, and the presentation entrypoint SHALL receive immutable context,
bounded tool outcomes, and bounded caller-supplied state and return only one
structured presentation. Mutable JavaScript state MUST NOT carry between
phases.

#### Scenario: Plan and present one result

- **WHEN** a planning entrypoint returns one valid declared tool request
- **THEN** the application may execute that request through the widget tool
  boundary
- **AND** invokes the presentation phase in a fresh environment with its
  structured outcome.

#### Scenario: Reject malformed phase output

- **WHEN** an entrypoint returns an unsupported type, additional fields,
  duplicate alias, cyclic value, non-finite number, or oversized structure
- **THEN** the phase fails with a controlled invalid-output failure
- **AND** the invalid value does not cross into tool dispatch or presentation.

### Requirement: Validated tool planning

Every planned tool request SHALL identify a stable registered tool and contract
version and contain only semantic arguments. Before dispatch, the application
SHALL verify the request against the program declaration, current grant,
per-execution call ceiling, and registered input contract, and the existing
widget gateway SHALL independently enforce tool eligibility, readiness, policy,
and cancellation.

#### Scenario: Dispatch a permitted planned request

- **WHEN** a plan returns a bounded request for a declared, granted, enabled,
  ready, and `WIDGET`-eligible tool
- **THEN** the application dispatches it once through the widget tool gateway
- **AND** returns its bounded canonical outcome under the request alias.

#### Scenario: Reject an invented transport

- **WHEN** a plan includes an arbitrary endpoint, URL, header, credential,
  timeout, callback, provider implementation, or unknown tool/version
- **THEN** the application rejects the request before any transport is opened.

### Requirement: Constrained presentation output

Presentation output SHALL conform to a versioned allowlisted tree of bounded
components, semantic style tokens, text, structured values, and
application-owned actions. It MUST NOT contain Compose code, HTML, executable
callbacks, arbitrary intents, arbitrary URI schemes, or references outside the
current structured tool outcomes and granted widget state.

#### Scenario: Produce a valid presentation

- **WHEN** the presentation entrypoint returns a bounded tree using granted
  components and actions
- **THEN** the application returns a normalized presentation model suitable for
  a later application-owned renderer.

#### Scenario: Reject executable presentation content

- **WHEN** a presentation contains code, a callback, an unsupported component,
  an ungranted action, or an unsafe link target
- **THEN** validation fails before any UI or external action is created.

### Requirement: Resource limits and controlled termination

Every phase SHALL enforce fixed application maxima for source and input size,
heap, stack, execution budget, collection and traversal size, tool-request
count, and output size. Cancellation, timeout, infinite or excessive work,
memory exhaustion, syntax/runtime error, and runtime crash MUST become bounded
controlled failures and MUST NOT terminate the application process or leave a
tool execution running without ownership.

#### Scenario: Terminate non-cooperative source

- **WHEN** a program loops indefinitely, recurses excessively, or exceeds its
  execution or memory budget
- **THEN** the runtime interrupts or destroys that isolated execution
- **AND** returns a controlled resource-limit failure within the documented
  outer deadline.

#### Scenario: Cancel an active execution

- **WHEN** the owning coroutine or future widget run is cancelled
- **THEN** cancellation reaches the active program phase and any owned tool call
- **AND** late output is discarded
- **AND** no automatic retry is started by the script runtime.

### Requirement: Runtime-only foundation

The script-runtime capability SHALL remain independent of widget authorship,
durable storage, navigation, UI rendering, scheduling, execution logs, and
production tool enablement. Executing a program through this foundation MUST
NOT persist it or create a user-visible widget.

#### Scenario: Execute a fixture program

- **WHEN** a test or future trusted caller executes a valid in-memory program
- **THEN** the runtime returns only its controlled execution result
- **AND** creates no durable definition, worker, screen, log history, or model
  session.

### Requirement: Typed result access for widget scripts

The runtime SHALL expose deterministic helpers to check one named outcome,
read an array at an exact path relative to a successful tool payload, and
construct a link node from an exact result URL path. It SHALL also offer a
seeded array-item selector that returns null for failed or empty outcomes and
otherwise returns the selected item and its exact path, plus a compact card
constructor for strings and presentation nodes. For simple array cards, a
single collection-card helper SHALL combine exact array/item field lookup,
first-item or seeded selection, failed/empty fallback, text nodes, and result-proven link
construction. Helpers SHALL accept only
bounded aliases and paths, SHALL NOT perform recursive name searches or select
the first matching type, and SHALL NOT expand script authority. The existing
outcome object shape and direct access SHALL remain valid for stored programs.

#### Scenario: Read one collection from multiple outcomes

- **GIVEN** two successful outcomes with different aliases
- **WHEN** a script reads an array from one alias and an explicit field path
- **THEN** only that alias and path supply the returned array.

#### Scenario: Missing or mistyped field

- **WHEN** a script requests an absent field, an invalid path, or a value that
  is not an array
- **THEN** the helper fails in a controlled manner rather than returning an
  unrelated array or an empty collection.

#### Scenario: Result-proven article link

- **WHEN** a script creates a link from an item URL path
- **THEN** the link carries the exact alias and path and remains subject to the
  existing presentation provenance and URL allowlist checks.

#### Scenario: Deterministic item card

- **WHEN** a script selects an item from a successful nonempty array using the
  granted seed and passes its text and result-proven link to the card helper
- **THEN** the same runtime snapshot selects the same index and yields a valid
  presentation card without script-authored random-index or card-envelope code.
- **AND** a failed or empty outcome can yield a fallback card without a result
  path lookup or link construction.

#### Scenario: Collection card from a nested result

- **WHEN** a script supplies an exact array path and item-relative title, body,
  and URL fields to the collection-card helper
- **THEN** the helper builds a card from a seeded item and a link carrying the
  exact alias and result-relative URL path.
- **AND** on failed or empty outcomes it returns the supplied fallback card
  before reading the array or constructing a link.

#### Scenario: Select first without a seed

- **WHEN** a collection card requests `first` and receives a nonempty array
- **THEN** it renders the first item without requiring a seed.
- **AND** `seeded_random` selects with the granted seed; omitting the selection
  field preserves the existing seeded behavior for stored programs.

#### Scenario: Proven link from a three-level array path

- **WHEN** a selected result array has a three-segment path such as
  `data.history.events`
- **THEN** the helper and presentation validator accept the exact five-segment
  item URL path, including its numeric item index, without a recursive search.

### Requirement: Bounded collection-card title suffix

A collection card MAY name one string or safe integer field from its selected result item as `titleSuffixField`. The runtime SHALL append that field's value in parentheses to the display title and default link label while retaining the selected item's URL provenance. Missing, invalid, or overlong suffix values SHALL fail validation. Cards without this field SHALL retain their existing title behavior.

#### Scenario: Integer year on a selected event

- **WHEN** the selected event has title `Don Giovanni`, year `1787`, and a proven HTTPS URL
- **THEN** the card title and default link label are `Don Giovanni (1787)` and the link retains the selected event's URL provenance.

#### Scenario: Missing year field

- **WHEN** a card requests `titleSuffixField=year` but the selected item has no year
- **THEN** execution fails rather than presenting a title without the requested suffix.
