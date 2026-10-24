## MODIFIED Requirements

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
