## Purpose

Define how ArarAI validates and executes untrusted JavaScript widget programs
through explicit capabilities, deterministic inputs, bounded tool plans, and
constrained presentation output without granting ambient application access.

## ADDED Requirements

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
explicitly as immutable execution inputs. Hidden wall-clock access and ambient
randomness MUST NOT be available; a replay with the same program, grant,
context, seed, tool results, and history input SHALL produce the same tool plan
and presentation result.

#### Scenario: Resolve the current local date

- **WHEN** a program derives a Wikipedia query from the supplied local date,
  timezone, and locale
- **THEN** it uses the values in its execution context
- **AND** does not read a separate runtime clock.

#### Scenario: Replay a seeded selection

- **WHEN** the same program execution is replayed with identical inputs and
  pseudo-random seed
- **THEN** every supported pseudo-random choice produces the same result.

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
