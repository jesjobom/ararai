## ADDED Requirements

### Requirement: Select refresh cadence in a dedicated authoring stage

For new schema-version-3 authoring sessions, achievable feasibility SHALL advance to a distinct schedule stage before algorithm generation. Only that stage SHALL receive the six new-choice intervals of 1, 2, 4, 6, 12, and 24 hours; an editing session MAY also retain its current interval. It SHALL also offer no-periodic-refresh and return exactly one permitted choice. It SHALL select the closest offered cadence when a requested cadence is not exactly supported, without introducing a frequency-specific clarification outcome. The accepted schedule artifact SHALL be authoritative for the draft; feasibility SHALL neither request nor return a periodic interval. Existing schema-version-1 sessions SHALL retain their original stage path and schedule meaning; schema-version-2 sessions SHALL retain the separate schedule stage and their four-field feasibility artifact with a null interval.

#### Scenario: Four updates per day

- **WHEN** a user requests four updates per day
- **THEN** feasibility omits the schedule field and the dedicated stage selects the offered 6-hour interval
- **AND** the draft uses the accepted schedule-stage interval.

#### Scenario: Closest supported cadence

- **WHEN** a requested periodic cadence is not exactly supported
- **THEN** the stage chooses the closest supported option instead of asking for clarification about the frequency
- **AND** the application rejects values outside the offered set.

#### Scenario: Legacy in-progress session

- **WHEN** a schema-version-1 session resumes after upgrading the app
- **THEN** it retains its prior feasibility-to-algorithm transition and persisted feasibility schedule.

#### Scenario: In-progress session with the earlier dedicated schedule contract

- **WHEN** a schema-version-2 session resumes after upgrading the app
- **THEN** its feasibility contract still requires a null periodic interval field
- **AND** it retains the separate schedule stage without discarding accepted checkpoints.

### Requirement: Change a saved widget's cadence from its details

The widget detail screen SHALL offer a numeric whole-hour field accepting every interval from 1 through 24, and a manual-only option. Selecting one SHALL persist the new interval and updated consent identity without regenerating the widget program, then reconcile periodic work. An in-flight run bound to the previous consent identity SHALL not commit as the active result. Previously saved intervals above 24 hours SHALL remain readable and executable until the user changes them.

#### Scenario: Change the refresh frequency

- **WHEN** a user selects a different supported interval on a saved widget's detail screen
- **THEN** the stored definition and detail screen reflect the selected interval
- **AND** the program revision remains unchanged while periodic work is reconciled.

## MODIFIED Requirements

### Requirement: Validate feasibility and a typed algorithm before code

For new schema-version-3 sessions, the first stage SHALL return exactly one
`outcome`, one bounded outcome-dependent `message`, and the minimum registered
tool IDs; its model-facing schema and artifact SHALL NOT include a periodic
interval. Earlier session versions SHALL retain their original four-field
feasibility contract and schedule semantics. The application SHALL derive protocol metadata,
enablement, eligible registered tool versions, deterministic runtime grants,
allowlisted presentation grants, and terminal normalization from checked-in
policy. For an achievable request, a later stage SHALL return a bounded typed
acyclic algorithm whose steps and dependencies distinguish runtime inputs,
independent planned tool calls, transformations, and the final presentation
objective. The application SHALL validate both artifacts before requesting
JavaScript.

Tool identity SHALL be authoritative only for a `tool_call` step. The
application SHALL resolve that ID to the exact registered version already
frozen by feasibility and SHALL NOT ask the model to repeat a contract version.
It SHALL accept and discard legacy `contractVersion` wire values without
granting authority, while continuing to reject unknown fields, invented tool
IDs, and capability expansion on actual tool calls.

Per-step runtime grants SHALL NOT be a model decision. The application SHALL
derive runtime authority exclusively from the validated feasibility envelope
and registered contracts, SHALL omit `runtimeInputs` from the algorithm capture
schema and normalized artifact, and MAY accept and discard that field from
legacy wire artifacts without granting any capability. Tool metadata SHALL be
required only for `tool_call` steps; its omission from other step kinds SHALL
not invalidate the algorithm.

#### Scenario: Derive the frozen tool version

- **WHEN** an algorithm selects a tool ID that exists in the frozen feasibility
  envelope and omits `contractVersion`
- **THEN** the application resolves the exact frozen capability and version
  for the normalized tool-call step
- **AND** a legacy wire version, whether absent, matching, or inconsistent,
  cannot alter that application-owned selection.

The feasibility instruction SHALL state the version-appropriate conditional
contract for all three outcomes: `message` is the display name for `achievable`, the
reason for `unachievable`, and one question for `needs_clarification`. It SHALL
ask the model to select only the minimum required available tool IDs without
treating not-yet-validated selections as frozen. A bounded repair SHALL
translate its controlled failure code into an actionable checked-in correction
without including arbitrary validator or exception text.
The wire artifact SHALL NOT require separate `reason` or
`clarificationQuestion` fields; the application SHALL derive both nullable
values from `outcome` and `message`.

#### Scenario: Accept a feasible capability plan

- **WHEN** the model selects only registered `WIDGET`-eligible tool IDs for an
  achievable request
- **THEN** the application resolves eligible versions, derives bounded runtime
  and presentation grants, and freezes that validated capability envelope for
  every later stage and repair
- **AND** later artifacts cannot broaden it.

#### Scenario: Normalize legacy runtime labels

- **WHEN** an otherwise valid algorithm includes a legacy `runtimeInputs`
  field with derived labels such as `month` or `day`
- **THEN** the application discards the entire field and continues using only
  runtime grants derived from the frozen feasibility envelope
- **AND** the normalized algorithm passed to later stages contains no
  model-authored per-step runtime grants.

#### Scenario: Report an unavailable operation

- **WHEN** the request requires an operation for which no registered eligible
  tool or supported runtime capability exists
- **THEN** the model may return `unachievable` with a bounded controlled
  explanation
- **AND** the application persists the controlled feasibility outcome for
  review without generating code, confirming a widget, or invoking a provider.

#### Scenario: Request clarification

- **WHEN** the request is too ambiguous to freeze a safe capability envelope
- **THEN** the model may return `needs_clarification` with one bounded question
- **AND** the attempt ends so the user can revise the prompt explicitly.
