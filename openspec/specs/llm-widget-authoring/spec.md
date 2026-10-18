# llm-widget-authoring Specification

## Purpose
Coordinate where and when local widget authorship runs so a requested
generation continues as an application-scoped background job with visible
state, cancellation, single-flight enforcement, and device-state deferral,
without changing the authoring pipeline stages or confirmation flow.

## Requirements

### Requirement: Application-scoped authoring jobs

The application SHALL run each user-authorized widget-authoring stage attempt
as an application-scoped job that continues while the user navigates within the
app and survives leaving the authoring screen while the process lives. The job
SHALL use the shared application-scoped local LLM engine, SHALL NOT create a
second concurrent engine instance, and SHALL end after persisting the attempt
outcome and unloading the model.

Previously accepted checkpoints SHALL remain durable when an active attempt is
cancelled, interrupted, or killed with the process. The application SHALL NOT
infer a successful artifact from an attempt that did not transactionally reach
its completed state.

#### Scenario: Generation continues after leaving the screen

- **WHEN** the user starts one stage attempt and navigates away from the
  authoring screen without cancelling
- **THEN** that attempt keeps running at application scope
- **AND** returning to the authoring screen shows the same durable session and
  attempt state.

#### Scenario: Process death is reported, not silently ignored

- **WHEN** the process dies while a later stage attempt is in flight
- **THEN** the in-flight attempt is reported as interrupted after restart
- **AND** every previously accepted checkpoint and consumed retry count remains
  available
- **AND** the user may retry the interrupted stage without repeating accepted
  upstream stages.

### Requirement: Single-flight authoring

The application SHALL allow at most one non-terminal widget-authoring session
and at most one queued, deferred, or running stage attempt at a time. Starting
a new authoring request while a session already exists SHALL open the existing
session with resume or discard controls instead of creating a competing
session. A duplicate action for the same session revision SHALL be idempotent.

#### Scenario: New request finds an existing session

- **WHEN** the user requests another widget generation while an existing
  authoring session is awaiting review, retry, or an active attempt
- **THEN** the new request does not create another session or model generation
- **AND** the application opens the existing session with its current
  actionable state.

#### Scenario: Second request refused while a job is active

- **WHEN** the user requests another widget generation while a stage attempt is
  queued, deferred, or running
- **THEN** the request is refused with an explanation that authoring is already
  in progress
- **AND** the active session and attempt are unaffected.

#### Scenario: Duplicate notification action is idempotent

- **WHEN** the same notification action is delivered more than once for one
  session revision
- **THEN** at most one state transition and one model generation occurs.

### Requirement: Cancelable background authoring

The user SHALL be able to cancel the active stage attempt from the authoring UI
or its foreground notification. Cancelling SHALL stop active generation,
release model resources, persist the attempt as cancelled, and retain
previously accepted checkpoints. When no attempt is active, the user SHALL be
able to discard the complete authoring session, which removes its transient
attempts and checkpoints without changing a confirmed widget.

#### Scenario: Cancel from the notification

- **WHEN** the user taps cancel while a stage attempt is running
- **THEN** active model work stops and the attempt becomes cancelled
- **AND** accepted upstream checkpoints remain available
- **AND** the session returns to an actionable retry or reprocess state.

#### Scenario: Discard an inactive session

- **WHEN** the user explicitly discards an authoring session while no attempt
  is running
- **THEN** its checkpoints, candidates, and attempt history are deleted
- **AND** no existing confirmed widget or revision is changed.

### Requirement: Device-state deferral

The application SHALL evaluate device state using the established thermal,
battery-temperature, process-memory, available-memory, low-memory, and
consecutive-sample criteria before loading the model for every stage attempt.
The available-memory criterion SHALL include a bounded requirement derived
from the selected model artifact in addition to the system-wide free-memory
fraction. A recovery-window expiry SHALL be reported separately from a model
generation watchdog expiry.
If state is unacceptable, the user-authorized attempt SHALL remain cancellable
while the model stays unloaded and SHALL wait at most 10 minutes for a stable
window. Failure to stabilize SHALL persist a controlled retryable outcome and
SHALL NOT advance the workflow.

After every generation outcome, the application SHALL persist the attempt,
unload the model, and end foreground execution. It SHALL NOT automatically
reload the model or start another generation until a subsequent explicit user
action.

#### Scenario: Wait with the model unloaded

- **WHEN** the user starts a stage attempt while the recovery criteria are not
  stable
- **THEN** the attempt waits with a visible reason while the model remains
  unloaded
- **AND** the user may cancel the wait
- **AND** generation begins only after the required stable samples are
  observed.

#### Scenario: Defer under severe thermal status

- **WHEN** a stage attempt starts while thermal status is severe or worse
- **THEN** it remains deferred with the reason visible while the model stays
  unloaded
- **AND** no generation runs while the unacceptable state persists.

#### Scenario: Exhausted retry budget fails explicitly

- **WHEN** the bounded recovery wait ends without observing an acceptable
  device state
- **THEN** the attempt persists `device_recovery_timed_out`, not the model
  generation `timed_out` failure
- **AND** the UI and notification expose Retry without advancing the workflow.

#### Scenario: Inter-generation recovery needs more than three minutes

- **WHEN** a user-authorized stage attempt has not observed an acceptable
  stable window after three minutes
- **THEN** it continues waiting instead of failing
- **AND** it waits for at most 10 minutes before persisting a retryable recovery
  timeout.

#### Scenario: Completed stage does not trigger another generation

- **WHEN** one model generation succeeds, fails validation, times out, or is
  cancelled
- **THEN** the application persists that outcome and unloads the model
- **AND** no recovery reload or later stage begins until the user explicitly
  requests it.

### Requirement: Visible authoring job state

The application SHALL expose the durable authoring session as a stage timeline
showing pending, queued, deferred, running, awaiting-review, awaiting-retry,
accepted, stale, interrupted, cancelled, and completed states as applicable.
While one attempt is active, a persistent foreground-service notification SHALL
match its state and offer cancellation. When the attempt ends, foreground
execution SHALL stop and a normal notification SHALL expose the next safe
review, continue, or retry action.

#### Scenario: Notification matches in-app state

- **WHEN** an active stage attempt reaches a persisted outcome
- **THEN** its foreground-service notification and service lifetime end
- **AND** the UI shows the same persisted outcome
- **AND** a normal notification may invite the user to review or trigger the
  next allowed action.

#### Scenario: Attempt completes before foreground-service delivery

- **WHEN** an attempt reaches its persisted outcome before a previously issued
  foreground-service refresh is delivered
- **THEN** the service first acknowledges the foreground start contract
- **AND** it immediately stops foreground execution without crashing the app
- **AND** the normal review, continue, or retry notification remains available.

#### Scenario: Presenter observes completion before service promotion

- **WHEN** a presenter requests foreground service start for an active attempt
  and immediately observes a review, retry, or absent-session state
- **THEN** it SHALL deliver a final refresh rather than stop the pending
  service directly
- **AND** the service SHALL promote itself before reading the mutable session
  state and detach or remove itself according to that latest state.

#### Scenario: Relaunch reconstructs the timeline

- **WHEN** the app restarts with a non-terminal authoring session
- **THEN** the timeline, accepted checkpoints, stale descendants, attempt
  history, and next allowed action are reconstructed from durable state rather
  than an in-memory job flow.

### Requirement: Select one complete page introduction from broader search

The widget-only `wikipedia_pages@2` tool SHALL search up to ten page candidates
using metadata, select one candidate by `first` or a granted deterministic
seed, and fetch only that page's complete lead introduction and canonical URL.
It SHALL NOT truncate an introduction: if it exceeds the explicit result
budget, it SHALL return `SummaryTooLarge`. Existing `wikipedia_pages@1`
revisions and Chat SHALL remain executable. New widget authoring SHALL see only
v2 in the page-search catalog, except an edit of a v1 widget SHALL also see its
referenced legacy contract. The widget runtime SHALL accept a complete bounded
introduction as one text node, while keeping its total JSON and presentation
byte limits and the separate durable-observation string limit.

#### Scenario: Search a topic and display one linked page

- **WHEN** a new widget searches for a random Wikipedia page about a topic
- **THEN** the model selects `wikipedia_pages@2` with `seeded_random` and
  `runtime.seed`, and receives one selected page with its full lead and
  canonical URL, independent of the candidate count.
- **AND** the result remains a page, not a verified historical event.

#### Scenario: Preserve a legacy widget

- **WHEN** a saved widget calls `wikipedia_pages@1`
- **THEN** dispatch and edit continue to recognize that exact version while
  new drafts are directed to v2.

### Requirement: Display complete page introductions and retain the last good card

A successful `wikipedia_pages@2` response SHALL identify its full `extract` as
the default collection-card body. A stored card that specifies only a title
SHALL display that introduction and the selected page link on its next
successful refresh. Explicit card body configuration SHALL take precedence;
legacy page-search responses SHALL keep their existing presentation behavior.
A typed provider failure SHALL produce a controlled run failure instead of
caching a fallback card as a successful refresh. The application SHALL retain
the last successful card and expose only an allowlisted diagnostic. HTTP 429
SHALL be distinguishable as rate limiting without exposing provider text.

#### Scenario: Refresh a saved title-only v2 card

- **WHEN** a saved collection card omits `body` and `wikipedia_pages@2`
  successfully returns a page
- **THEN** the card displays the full introduction and canonical link.

#### Scenario: Provider throttles a refresh

- **WHEN** a widget with a cached successful card receives HTTP 429 from
  Wikipedia
- **THEN** the run records a rate-limited controlled failure and keeps the
  previous card rather than replacing it with a fallback.

### Requirement: Keep prompt-language checks advisory

Authoring SHALL expose the runtime local-time API and tell the model that
descriptive transform steps do not execute. It SHALL NOT reject a call or plan
solely because a fixed word list found a semantic condition in the initial
request. Technical validation MAY enforce syntax, tool arguments, and choices
frozen in typed algorithm fields. Any future heuristic comparison between the
request and generated behavior SHALL be a nonblocking user-facing warning
with an option to regenerate, not an authoring failure.

#### Scenario: Semantic mismatch is not a technical validation error

- **WHEN** a call is structurally valid but its query depends on language
  instead of the local hour requested by the user
- **THEN** the keyword matcher does not block authorship; the user can review
  the call and choose to reprocess it.

#### Scenario: Advisory warning remains separate from stage success

- **WHEN** the initial request appears to ask for a random Wikipedia page but
  the algorithm chooses `first`, or a day/night request has a call source that
  does not appear to read local time
- **THEN** the application shows a localized warning beside the generated
  stage and offers reprocessing without changing its status or available
  continue/confirm actions
- **AND** a heuristic that cannot assess a request or artifact produces no
  warning rather than a validation failure.

#### Scenario: Requested page-selection order differs from the algorithm

- **WHEN** an unambiguous request appears to ask for random selection but the
  page tool uses `first`, or asks for the first result but the tool uses
  `seeded_random`
- **THEN** the algorithm stage shows a nonblocking warning identifying that
  possible mismatch.
- **WHEN** the request appears to ask for the last result
- **THEN** the stage warns that the current Wikipedia page tool supports only
  first and seeded-random selection; it does not pretend that last selection
  was implemented.

### Requirement: Preserve random-page intent at the page tool boundary

The typed algorithm step for a `wikipedia_pages@2` call SHALL freeze a
`selectionMode` of `first` or `seeded_random`. The call-stage context SHALL
distinguish tool-level page selection from presentation selection; the tool
returns only one page. Call, plan, and assembly validation SHALL require the
call arguments to match the frozen selection, including the current runtime
seed when `seeded_random` is chosen. This contract SHALL NOT infer the mode
by scanning words in the user's request. Seeded candidate selection SHALL
remain deterministic for a given seed while avoiding a fixed search-result
index across an exact hourly seed interval. Earlier checkpoints without a
selection mode SHALL remain readable.

#### Scenario: A random card cannot repair first-page tool selection

- **WHEN** the algorithm freezes `selectionMode: seeded_random` but the tool
  call uses `selection: first` and only the collection card says random
- **THEN** validation reports `InvalidToolSelection` and guides the model to
  use `selection: seeded_random` and `seed: runtime.seed` in the tool call.

#### Scenario: First-page selection remains available

- **WHEN** the algorithm freezes `selectionMode: first`
- **THEN** the tool call uses `selection: first` without a seed, regardless of
  the collection card's own item-selection setting.

#### Scenario: Hourly seed values can vary the selected candidate

- **WHEN** the same page query runs hourly with different runtime seeds
- **THEN** the selected candidate is derived by a deterministic seeded
  pseudorandom generator rather than direct seed modulo candidate count.

### Requirement: Preserve the initial request with the confirmed revision

The application SHALL retain the exact initial user request locally with each
confirmed widget revision, including after the transient authoring session is
removed. It SHALL show that request in the authoring timeline and widget detail
for comparison with the generated program. Existing revisions with no retained
request SHALL remain readable. The request SHALL NOT be included in widget run
diagnostics or automatically shared.

#### Scenario: Confirm a generated widget

- **WHEN** a staged authoring session is confirmed
- **THEN** its initial request is saved atomically with the new immutable revision
- **AND** is visible alongside that revision's source in widget details.

### Requirement: Concise result contract for render authoring

The render-authoring stage SHALL receive application-owned helper instructions
and a compact, schema-derived catalog of result field paths and types for its
selected tools. It SHALL NOT need to combine raw outcome-envelope syntax with
the complete tool output JSON Schema. The catalog SHALL retain the alias and
the item fields needed to render selected data; full tool schemas remain
authoritative for validation and dispatch.
The render context SHALL identify `outcomes` as a plain object keyed by alias,
show the success/failure entry shapes, and distinguish passing the complete
object to `collectionCard` from passing one selected entry.

#### Scenario: Author a Wikipedia events card

- **WHEN** an authoring stage selects `wikipedia_on_this_day@1`
- **THEN** the model receives the event-array path and item field names plus
  examples of seeded item selection, compact card construction, and
  result-proven links
- **AND** it need not write `outcomes[alias].payload.events` or a hand-assembled
  `sourceField`, `Math.random()`, or repetitive card envelopes to produce a
  valid card.

#### Scenario: Simple collection rendering

- **WHEN** the selected tool result exposes an array and the requested view is
  a card showing one item
- **THEN** the render stage receives `runtime.collectionCard` as the primary
  contract and need only supply the alias, exact array/item fields, and
  fallback text inside one call.
- **AND** it need not compose success checks, array reads, seeded-index logic,
  presentation nodes, or link provenance itself.
- **AND** if the selected item exposes an HTTPS `canonicalUrl`, the runtime
  includes a result-proven link even when the model omits the optional URL field.

#### Scenario: Success fixture renders fallback

- **WHEN** a render function produces the same fallback for a nonempty success
  fixture and for empty or failed fixtures
- **THEN** authoring validation rejects the render artifact instead of
  reporting a successful stage.

### Requirement: Progressive authoring capability disclosure

The feasibility stage SHALL see a compact catalog of available tools. After
tool selection, the algorithm stage SHALL see only selected tool contracts and
short render-capability descriptions, and SHALL select a render capability.
The feasibility catalog SHALL describe each tool's relevant input/output
limits without giving detailed instructions for an unselected tool.
Feasibility SHALL distinguish unavailable source data from rendering logic:
filtering or conditional display that the bounded custom renderer can perform
SHALL NOT be rejected merely because a collection card cannot perform it.
The render stage SHALL receive the detailed contract for only that selected
capability. Existing algorithm checkpoints without a render-capability choice
SHALL continue through the legacy JavaScript source path.

#### Scenario: Distinguish query search from date-only events

- **WHEN** Wikipedia page search and on-this-day events are both available
- **THEN** feasibility sees that page search accepts a query but returns pages,
  while on-this-day returns events for a date but accepts no topic or region
  query.
- **AND** feasibility does not receive on-this-day implementation guidance
  before it selects a tool.

#### Scenario: Select a simple collection card

- **WHEN** the algorithm selects `collection_card` for a tool-result array
- **THEN** the render stage sees its selected result-field catalog and a small
  data-only card contract, without the custom JavaScript or presentation-node
  contracts.
- **AND** the model submits alias, array path, item fields, mandatory fallback,
  and a `first` or `seeded_random` selection policy.
- **AND** the card capture schema restricts alias, array path, and item-field
  names to choices derived from selected tool output schemas; only fallback is
  literal display text.
- **AND** the application generates the render function and validates it with
  populated, empty, and failed result fixtures before accepting the artifact.

#### Scenario: Custom presentation remains available

- **WHEN** the algorithm selects `custom_source`, or a previously accepted
  algorithm checkpoint has no render-capability choice
- **THEN** the render stage uses the existing JavaScript source contract and
  validation path.

#### Scenario: Preserve post-result operations

- **WHEN** an algorithm requires filtering, comparison, conditional selection
  after a tool result, or consideration of multiple tool results
- **THEN** it selects `custom_source` and represents those operations in its
  ordered steps.
- **AND** a new `collection_card` algorithm with a transform after the tool
  call, a step depending on the tool result, or more than one tool call is
  rejected for repair before render authoring.
- **AND** a condition evaluated from runtime inputs before one tool call may
  still use `collection_card` when its result needs only direct item display.

#### Scenario: Preserve distinct title and description content

- **WHEN** a selected tool exposes separate title and description fields and
  the requested card needs both, including a transformed title
- **THEN** the custom-render authoring context describes the selected item
  fields' meanings and permits multiple text elements in one card.
- **AND** the render instruction does not suggest replacing the description
  with the transformed title merely to fit one display-text slot.

#### Scenario: Keep tool selection distinct from result selection

- **WHEN** the selected tools do not include a tool with a tool-call selection
  mode
- **THEN** the algorithm capture schema omits `selectionMode` from steps.
- **AND** selecting an item from a returned array belongs to a post-result
  transform and the selected render capability, not to unsupported tool
  arguments.
- **AND** an omitted dependency list on the first independent step is treated
  as empty; later steps must declare dependencies explicitly so data flow is
  not silently discarded.

#### Scenario: Required data is unavailable

- **WHEN** no available tool can supply a required user data source or query
- **THEN** the existing feasibility stage returns `unachievable` or
  `needs_clarification`, without substituting a different request.
- **AND** the application does not use a closed word list to block authoring.

### Requirement: Dedicated prompt-based widget authorship

The application SHALL let a user ask an explicitly verified installed local
model to create or edit an in-app widget in a dedicated authoring flow. The
application SHALL expose a bounded, user-reviewed sequence of isolated model stages for
feasibility/capability selection, typed algorithm design, JavaScript function
generation, orchestration, presentation, validation, and deterministic
assembly. Each stage SHALL receive only the bounded non-secret context and
previously validated artifacts required for its objective.

The bounded context SHALL state whether the dedicated flow is creating or
editing a widget and SHALL define the user's instruction as the desired widget
behavior/content. The application SHALL NOT require the user to repeat the
current UI operation or the word "widget" in that instruction.

#### Scenario: Propose a new widget

- **WHEN** the user asks an eligible installed model to create a supported
  widget
- **THEN** the authoring flow advances through the versioned pipeline only after explicit user continuation at each stage and
  captures one typed artifact from each required stage
- **AND** user-specific call functions and custom presentation JavaScript remain
  model-generated, while application-owned orchestration and simple collection
  cards may be compiled deterministically from accepted typed model artifacts
- **AND** the application, not the model, assigns identity, revision, digest,
  effective grants, hard resource limits, assembly order, and consent.

#### Scenario: Interpret behavior-only creation language

- **WHEN** the create-widget flow receives an instruction such as "show a
  historical event for today" without restating "create a widget"
- **THEN** every stage treats it as a widget-creation request
- **AND** does not request clarification merely because the operation or object
  type was supplied by the UI context instead of repeated by the user.

#### Scenario: Authoring model is unavailable

- **WHEN** no installed selected model is explicitly verified for widget
  authorship
- **THEN** creation and prompt-based editing are unavailable with a clear
  explanation
- **AND** listing, inspecting, enabling, disabling, refreshing, and deleting
  existing widgets remain available.

### Requirement: Validate feasibility and a typed algorithm before code

The first stage SHALL return exactly one `outcome`, one bounded
outcome-dependent `message`, one bounded periodic interval or null, and the
minimum registered tool IDs. The application SHALL derive protocol metadata,
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

The feasibility instruction SHALL state the four-field conditional contract
for all three outcomes: `message` is the display name for `achievable`, the
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

### Requirement: Recover the device between model generations

Each user-authorized stage or retry SHALL begin with the model unloaded, await a
bounded stable temperature-and-memory gate, and load the model with the fixed
authoring inference configuration. After persisting that attempt outcome, the
application SHALL unload the model and wait for another explicit user action.
Unload, wait, and replacement publication SHALL be serialized with model
lifecycle transitions. Native ownership transfer SHALL remain non-cancellable,
the wait SHALL remain
cancellable, and the UI plus sanitized lifecycle telemetry SHALL expose the
recovery and reload phases. The application SHALL NOT recover again for
terminal `unachievable` or `needs_clarification` outcomes.

#### Scenario: Start each later generation from a recovered model session

- **WHEN** the user explicitly starts the next stage or retries a rejected stage
- **THEN** the application waits while the model remains unloaded
- **AND** it publishes a freshly loaded text-only session using the same fixed
  authoring configuration only after the recovery gate is stable
- **AND** the next stage or repair starts only after that reload succeeds.

#### Scenario: Avoid a reload for a terminal feasibility outcome

- **WHEN** feasibility is validated as `unachievable` or
  `needs_clarification`
- **THEN** the attempt returns the controlled outcome without reloading the
  model or requesting an algorithm.

#### Scenario: Stop safely when recovery does not stabilize

- **WHEN** the bounded recovery gate does not observe stable temperature and
  memory conditions before its deadline
- **THEN** the authoring attempt ends with a controlled timeout while the model
  remains unloaded
- **AND** no later model request, provider call, checkpoint promotion, or
  capability expansion occurs.

#### Scenario: Fail safely when the replacement session cannot load

- **WHEN** the feasibility-to-algorithm reload fails before a replacement model
  session is published
- **THEN** the authoring attempt ends with the controlled model-load failure
- **AND** no algorithm request, provider call, checkpoint promotion, or
  capability expansion occurs.

#### Scenario: Repair an inconsistent feasibility outcome

- **WHEN** the feasibility artifact contains an invalid outcome-dependent
  message, schedule, or tool ID selection
- **THEN** the application identifies the controlled feasibility category
- **AND** the repair repeats the four-field contract and keeps a supported
  request on `achievable` before requesting one replacement artifact.

#### Scenario: Reject unsupported adaptive execution

- **WHEN** the proposed algorithm requires a later tool call whose arguments
  depend on an earlier live provider result
- **THEN** the application rejects it as unsupported by the v1 plan-all runtime
- **AND** does not approximate, reorder, or silently remove the dependency.

#### Scenario: Normalize irrelevant non-tool metadata

- **WHEN** a `runtime_input` or `transform` step carries a non-null `toolId` or
  `contractVersion` value in the shared bounded step shape
- **THEN** the application discards those non-authoritative values and retains
  the step with no tool capability
- **AND** still validates the exact field set and every real `tool_call`
  identity/version against the frozen envelope.

### Requirement: Generate and validate dynamic JavaScript by responsibility

For every validated planned tool call, the model SHALL generate one bounded
named JavaScript function that receives only the deterministic runtime stdlib
snapshot and declared inputs and returns one semantic call for the fixed
registered tool/version. The application SHALL derive the short `plan`
orchestrator deterministically from accepted call functions; a separate model
stage SHALL generate the `render` function. The application SHALL syntax-check and
execute each fragment with synthetic normal/boundary fixtures, validate tool
arguments and presentation output against current contracts, and accept no
fragment that uses undeclared inputs or capabilities.

The bounded program API context SHALL map natural requests for a random,
varied, shuffled, or selected item from a bounded list, including equivalent
supported user-language terms, to the `seed` runtime grant and
`runtime.seededIndex(length)`. It SHALL state that `Math.random` is unavailable,
that a fresh execution may select a different item, and that the same runtime
snapshot must reproduce the same index.

#### Scenario: Translate natural variation safely

- **WHEN** the user asks for one random or varied item from a bounded tool
  result without naming an implementation primitive
- **THEN** an achievable artifact selects the `seed` runtime grant
- **AND** generated code uses `runtime.seededIndex(length)` rather than
  `Math.random` or another ambient source of nondeterminism.

#### Scenario: Validate one generated tool-call function

- **WHEN** a generated function returns a valid call for its frozen
  tool/version across bounded synthetic runtime fixtures
- **THEN** the application persists that exact fragment as a reviewable
  candidate in the private session, not yet an accepted checkpoint
- **AND** later model stages receive its immutable signature and required
  behavior without authority to rewrite it.

#### Scenario: Resolve current time dynamically

- **WHEN** generated behavior needs the current local date or time
- **THEN** it reads the fresh immutable execution snapshot through the granted
  deterministic runtime stdlib
- **AND** does not bake the authoring date into source or read an ambient clock.

#### Scenario: Validate orchestration and presentation separately

- **WHEN** all required call functions are accepted
- **THEN** the application derives and validates the `plan` orchestration against
  the typed algorithm and validates `render` against synthetic success,
  failure, and empty tool outcomes
- **AND** an invalid presentation cannot invalidate or rewrite an accepted call
  function.

### Requirement: Assemble deterministically and repair only the failing stage

The application SHALL assemble the final source from exact accepted source
fragments in a fixed application-owned order and SHALL perform
a complete synthetic dry-run of the exact package before preview. A later model
round MUST NOT rewrite accepted fragments. Each user-authorized stage attempt
SHALL have checked-in artifact-size, timeout, and stage-local retry limits. A
repair SHALL require an explicit user action, remain scoped to the failed stage,
and never expand the frozen
capability envelope or rewrite an accepted checkpoint.

#### Scenario: Repair a rejected fragment

- **WHEN** a fragment fails deterministic validation, stage-local repair
  budget remains, and the user chooses Retry
- **THEN** the application supplies immutable accepted dependencies, the
  relevant contract, a controlled failure code, and the rejected artifact only
  when a first repair has a localized validation category
- **AND** revalidates the replacement without exposing provider data, secrets,
  stack traces, or unrelated artifacts.

#### Scenario: Localize a feasibility rejection

- **WHEN** deterministic feasibility validation rejects an artifact
- **THEN** the application classifies it as JSON/root, exact field set,
  protocol, display name, enabled value, schedule, tool selection, runtime
  grant, presentation grant, outcome contract, or resource limit
- **AND** uses that controlled category in the bounded repair and sanitized
  diagnostic, recording its attempt number and argument size while exporting
  neither the rejected value nor arbitrary validator detail
- **AND** a debug build may emit only the same controlled attempt metadata to a
  dedicated Logcat tag for user-initiated local ADB collection.

#### Scenario: Isolate feasibility timeout factors

- **WHEN** the user selects a local feasibility diagnostic probe
- **THEN** the application runs exactly one fresh-conversation feasibility
  generation using either full context plus the natural request, compact
  feasibility context plus the natural request, or compact context plus an
  explicit implementation control
- **AND** the report identifies the selected mode and includes only sanitized
  input byte counts, estimated input characters, context hash, controlled
  outcome/capture metadata, and monotonic first-event, capture, terminal,
  watchdog, return, and cleanup-overrun timings
- **AND** excludes the prompt, context text, generated arguments, raw model
  output, source, exception text, and stack trace.

#### Scenario: Isolate the production-like algorithm stage

- **WHEN** the user selects the isolated natural algorithm diagnostic
- **THEN** the application runs exactly one fresh-conversation algorithm
  generation from an application-owned, normalized achievable feasibility
  fixture using the production natural prompt, context, schema, and parser
- **AND** invokes no provider and persists, schedules, or executes nothing
- **AND** reports only sanitized input/lifecycle signals, controlled outcome,
  and aggregate callback metadata while excluding prompt, context, generated
  arguments, provider data, source, exception text, and stack trace.

#### Scenario: Run the production-like pipeline from a cold process

- **WHEN** the user selects a cold authoring diagnostic from a fresh process
- **THEN** it exercises only an explicitly requested stage or isolated probe,
  without automatically running the complete authoring pipeline
- **AND** it applies the production stage contract, validation, and
  sanitization without creating a confirmed widget or schedule.

#### Scenario: Observe every complete-pipeline attempt without content

- **WHEN** a legacy complete-pipeline diagnostic record is inspected
- **THEN** its sanitized report distinguishes stages, attempt outcomes, and
  callback timing without exposing prompt, generated source, provider result,
  exception text, or stack trace
- **AND** production authoring does not use that continuous diagnostic path.

#### Scenario: Exhaust repair budget

- **WHEN** the current stage exhausts its persisted retry budget without a
  valid candidate
- **THEN** it records a controlled stage-specific result and awaits user action
- **AND** no widget revision is confirmed, scheduled, or executed.

#### Scenario: Dry-run the exact assembled package

- **WHEN** all required fragments pass their local validators
- **THEN** the application assembles them without model rewriting and executes
  the exact package with synthetic runtime and tool outcomes
- **AND** only a successful plan/render dry-run may proceed to draft preview.

### Requirement: Validate before preview or persistence

Every stage artifact and the exact assembled package SHALL pass strict schema,
source/package, frozen-capability, tool/version, schedule, presentation,
provenance, and resource validation before the draft can be confirmed.
Before final confirmation, authoring MUST NOT execute a production tool,
contact a provider, persist a confirmed widget revision, register work, or
activate a widget. Bounded candidates, failures, and accepted checkpoints MAY
be persisted only in the private transient authoring session.

#### Scenario: Preview a valid proposal

- **WHEN** a proposal passes all validation
- **THEN** the application shows a normalized behavior summary, source,
  planned tool/capability permissions, schedule, data retention, and expected
  presentation scope
- **AND** labels that view as an unconfirmed draft.

#### Scenario: Reject invented or unsafe behavior

- **WHEN** a proposal references an unknown or ineligible tool, arbitrary
  endpoint, unsupported API, forbidden capability, invalid source, unsafe
  presentation action, or excessive limit
- **THEN** the application rejects it with a controlled user-facing reason
- **AND** does not confirm, schedule, or execute a widget from the proposal;
  a bounded rejected candidate MAY remain in the private session for review.

#### Scenario: Model returns no valid stage artifact

- **WHEN** a stage ends, fails, times out, or is cancelled without exactly one
  valid structured artifact and no bounded repair succeeds
- **THEN** the authoring attempt ends without changing any confirmed widget
- **AND** the UI identifies the failed stage with a controlled reason.

### Requirement: Observable cancellable authoring progress

The foreground authoring UI SHALL expose the active bounded stage, call-function
index/count when applicable, validation activity, and repair attempt/count. It
SHALL permit cancellation throughout generation and validation without
presenting intermediate source as a confirmable draft.

Debug runtime diagnostics SHALL emit sanitized process-local lifecycle events
that distinguish engine initialization/reuse, conversation creation/reuse,
request submission, first/terminal callback, cancellation, close, cleanup, and
overlapping active generations. The events SHALL include bounded timing plus
available thread-count, RSS, swap, total/native PSS, native/Java heap, available
system memory, low-memory state, and thermal-status signals, and SHALL NOT
include model paths or identifiers, prompts, generated content, tool arguments,
exception messages, provider data, or stack traces. Disposed conversation
tracking SHALL preserve identity-based idempotence without keeping discarded
native wrappers strongly reachable.

#### Scenario: Show multi-round progress

- **WHEN** a user-authorized stage attempt starts or reaches a durable result
- **THEN** the UI updates its timeline among feasibility, device recovery, model
  load, tool selection, algorithm design, call generation N/M, orchestration,
  presentation, validation, review, retry, and draft-ready states
- **AND** only an explicitly accepted checkpoint becomes an upstream input; no
  intermediate artifact is an executable widget.

#### Scenario: Cancel during a later stage

- **WHEN** the user explicitly cancels during generation, repair, or validation
- **THEN** active work and ephemeral conversations are released and late
  callbacks are discarded
- **AND** previously accepted checkpoints remain durable while no confirmed
  widget revision is changed. Leaving the screen alone does not cancel work.

#### Scenario: Diagnose a native stage stall

- **WHEN** a debug authoring round times out before its first generation event
- **THEN** local runtime telemetry identifies whether the engine was reloaded,
  a conversation was created or reused, another generation overlapped, and
  where cancel/close/cleanup time was spent
- **AND** reports only process-local identifiers and sanitized resource signals.

#### Scenario: Observe ephemeral conversation release

- **WHEN** an ephemeral debug conversation is cancelled or closed
- **THEN** telemetry captures sanitized process-memory checkpoints before and
  after the operation so native heap and PSS release can be compared
- **AND** the discarded wrapper remains eligible for garbage collection after
  identity-based duplicate-disposal protection is no longer needed.

### Requirement: Explicit confirmation and least privilege

The application SHALL derive the effective grant from the validated proposal,
available tool contracts, fixed policy, and the user's confirmation. The user
MUST explicitly confirm creation or any edit before it becomes active, and the
confirmation SHALL disclose background frequency, network tools, retained
history, and user actions. No proposal may silently enable a tool or provider.

#### Scenario: Confirm and enable a draft

- **WHEN** the user accepts a valid draft through an action that explicitly
  states it will create and enable the disclosed schedule
- **THEN** the application persists the new revision and consent record
- **AND** schedules only the confirmed effective grant.

#### Scenario: Decline a draft

- **WHEN** the user cancels or leaves an unconfirmed draft
- **THEN** no widget, revision, consent, schedule, or production tool call is
  created.

#### Scenario: Required tool is disabled

- **WHEN** a valid draft requires a registered tool that the user has not
  enabled
- **THEN** the application keeps the draft blocked from activation
- **AND** offers an explicit enablement path without changing tool state
  silently.

### Requirement: Controlled prompt-based editing

Editing SHALL supply the relevant stages with the current normalized managed
definition and active source but not unrelated widget data, credentials, raw
execution logs, or authoring history. The assembled result SHALL be a complete
replacement draft, and the application SHALL show a revision diff and highlight
added tools, capabilities, data retention, presentation actions, or increased
frequency before confirmation.

#### Scenario: Propose a safe edit

- **WHEN** the user asks to change an existing widget's title, logic,
  presentation, or supported schedule
- **THEN** the application validates a complete replacement proposal
- **AND** shows its behavior/source diff while retaining application-owned
  widget identity and ownership.

#### Scenario: Expand authority during editing

- **WHEN** an edit requests an additional tool, capability, retained data,
  external action, or more frequent schedule
- **THEN** the preview calls out the expansion explicitly
- **AND** the previous consent cannot authorize the new revision.

### Requirement: No unattended model execution

The language model SHALL participate only in an explicit foreground authoring
attempt. Confirmed widget refresh, rendering, history lookup, recovery, and
scheduling MUST use the stored validated program and MUST NOT invoke a model to
choose, repair, or reinterpret behavior.

#### Scenario: Refresh an LLM-authored widget

- **WHEN** a confirmed widget executes manually or in the background
- **THEN** the stored active program executes without constructing an
  authoring conversation or loading a model.

#### Scenario: Stored program later becomes invalid

- **WHEN** an active revision becomes incompatible or fails validation
- **THEN** the application marks it as needing attention
- **AND** does not ask a model to change it until the user explicitly starts an
  edit.

### Requirement: Local private authoring data

Authoring prompts, bounded stage candidates, rejected artifacts, accepted
checkpoints, draft source, validation output, repair context, and model protocol
SHALL remain local and app-private while a session is active and SHALL be
excluded from backup. The
dedicated authoring exchanges MUST NOT be added to normal Chat history,
execution logs, sanitized diagnostics, analytics, or backups, and tool
credentials MUST NOT enter model context or stage fields. The only export
exception SHALL be an explicitly confirmed raw sidecar for a user-initiated
diagnostic in a debuggable build; it MUST remain unavailable in release builds
and MUST NOT create an automatic upload or telemetry path.

#### Scenario: Finish an authoring attempt

- **WHEN** one stage attempt succeeds, fails, or is cancelled
- **THEN** its ephemeral model conversation and in-memory execution artifacts
  are released while bounded attempt details and accepted checkpoints remain
  durable in the private transient session
- **AND** only final confirmation may promote a validated widget revision and
  consent record; confirmation or explicit discard deletes the transient session.

#### Scenario: Explicitly export a raw local diagnostic

- **WHEN** a user running a debuggable build explicitly confirms the raw-data
  warning after a local diagnostic
- **THEN** the application writes a replace-on-export cache file containing the
  exact bounded stage inputs and captured tool-argument strings
- **AND** shares it only through a temporary read grant while leaving the
  normal report and Logcat sanitized
- **AND** a release build exposes no equivalent control or raw capture.

### Requirement: Durable stage checkpoints and attempts

The application SHALL persist a versioned authoring session, append-only stage
attempts, and explicitly accepted checkpoints for feasibility, algorithm, every
required call function, plan, and render. Each session SHALL freeze the user
instruction, target widget identity when editing, selected model artifact,
inference configuration, authoring protocol/schema versions, and relevant tool
contract digest. Each checkpoint SHALL record an artifact digest and the exact
accepted upstream digest on which it depends.

Only a completed and validated candidate explicitly accepted by the user SHALL
become an authoritative checkpoint. Local assembly validation MAY run
automatically after render is accepted but SHALL use only current, non-stale
checkpoints.

Protocol version and source-fragment identity/signature SHALL be derived from
the session and current stage rather than requested from the model. The plan
checkpoint SHALL be generated deterministically by the application from the
accepted call-function order and SHALL NOT load or invoke the model. New call
functions SHALL return only tool arguments; the application-owned plan SHALL
add the frozen alias, tool id, and contract version. Previously accepted legacy
envelopes and complete-call functions MAY be normalized for compatibility but
SHALL NOT expand authority.

#### Scenario: Successful attempt awaits acceptance

- **WHEN** one stage generation returns an artifact that passes deterministic
  parsing and validation
- **THEN** the application persists it as a reviewable candidate
- **AND** it does not use that candidate as an upstream checkpoint until the
  user chooses Continue.

#### Scenario: Accepted checkpoint unlocks the next stage

- **WHEN** the user accepts a successful candidate
- **THEN** it becomes the authoritative checkpoint for that stage
- **AND** only the next dependency-satisfied stage becomes eligible to run.

#### Scenario: Deterministic plan does not invoke the model

- **WHEN** the user starts the dependency-satisfied plan stage
- **THEN** the application derives and validates its source from the accepted
  call functions and algorithm
- **AND** no device recovery wait, model load, or generation occurs.

#### Scenario: Incomplete write cannot create a checkpoint

- **WHEN** process death or storage failure interrupts attempt persistence
- **THEN** no partial candidate or accepted checkpoint is exposed
- **AND** the last committed session revision remains authoritative.

### Requirement: Inspectable safe execution details

For every attempt, the application SHALL show the stage and revision, attempt
kind and number, status, duration, captured byte count, controlled validation
result, and bounded captured JSON or source when available. Captured content
SHALL be rendered only as escaped plain text and SHALL NOT be executed,
interpreted as rich content, sent to a provider, or treated as a capability
grant merely because it is displayed or persisted.

The user-visible record SHALL exclude chain-of-thought, reasoning tokens, full
internal prompts, secrets, raw exception text, stack traces, model paths, and
unbounded logs. If generation fails before a bounded artifact is captured, the
application SHALL say that no artifact was available instead of fabricating or
exposing unrelated content.

#### Scenario: Inspect a successful code stage

- **WHEN** a call-function, plan, or render candidate is captured and validated
- **THEN** the user can inspect the exact bounded source and successful
  validation summary as inert text before accepting it.

#### Scenario: Inspect a rejected artifact

- **WHEN** a bounded artifact is captured but deterministic validation rejects
  it
- **THEN** the user can inspect the inert captured content and controlled
  failure category
- **AND** no rejected source is executed or included in final assembly.

#### Scenario: Failure occurs before capture

- **WHEN** model loading, timeout, cancellation, or tool-call parsing fails
  before an artifact is captured
- **THEN** the attempt shows the controlled failure and states that captured
  content is unavailable
- **AND** it exposes no raw internal exception or generated token stream.

### Requirement: User-controlled retry and reprocessing

Each explicit user action SHALL authorize at most one model generation. A
failed stage SHALL expose Retry while its persisted repair budget remains. A
retry SHALL receive only immutable accepted dependencies, the relevant checked-
in contract, a controlled failure code, and the immediately preceding bounded
rejected artifact only when the first repair has a precise localized validation
category. Source-envelope and render-execution repairs SHALL omit rejected
source. A second repair SHALL omit the previous artifact for every category.
Restarting the process SHALL NOT reset consumed repair budget.

An accepted stage SHALL expose Reprocess. Reprocessing SHALL create a new stage
revision without overwriting the accepted checkpoint. If the replacement fails
or is abandoned, the previous accepted checkpoint and descendants remain
authoritative. If the user accepts a successful replacement, every transitive
downstream checkpoint SHALL atomically become stale and SHALL be excluded from
context construction, preview, and final assembly until regenerated.

#### Scenario: Retry only the failed stage

- **WHEN** a stage attempt fails deterministic validation and repair budget
  remains
- **THEN** Retry starts exactly one repair attempt for that stage
- **AND** no accepted upstream stage is regenerated or changed.

#### Scenario: Repair budget survives restart

- **WHEN** the app restarts after one or more retries of the current stage
- **THEN** the remaining repair count is unchanged
- **AND** exhausted stage-local repair authority cannot be restored by process
  recreation.

#### Scenario: Render execution repair regenerates without source anchoring

- **WHEN** a render candidate fails controlled sandbox execution or a first
  repair is rejected
- **THEN** the next repair receives the checked-in generic render contract and
  controlled failure category without the rejected source
- **AND** each valid alias remains available only as bounded execution data.

#### Scenario: Failed replacement preserves the working graph

- **WHEN** the user reprocesses an accepted stage and the replacement fails
- **THEN** the existing accepted checkpoint and its descendants remain
  authoritative
- **AND** the failed replacement remains visible only in attempt history.

#### Scenario: Accepted replacement invalidates descendants

- **WHEN** the user accepts a replacement for an upstream stage
- **THEN** every transitive downstream checkpoint is marked stale in the same
  durable transition
- **AND** stale content cannot be used to build later prompts or a widget draft.

### Requirement: Normalized feasibility and actionable presentation validation

For an achievable feasibility result, the model-facing contract SHALL request
a concise display title of at most 80 characters. A non-blank overlong title
SHALL be normalized deterministically by the application; a blank title SHALL
remain invalid. The render contract SHALL enumerate exact node shapes, allowed
tones, and explicit success, empty, and failed-outcome handling before payload
access.

Presentation validation SHALL expose bounded controlled categories for invalid
node type, invalid fields, invalid tone, invalid link provenance, missing
empty-result handling, and missing failed-outcome handling. It SHALL NOT expose
raw JavaScript exceptions or generated content through those categories.

The render request SHALL contain a stage-specific generic API contract. It SHALL
describe outcomes as a plain JavaScript object accessed with bracket notation,
forbid Map-style `get()`, and require aliases to be copied from `toolResults`.
Generic contract examples SHALL NOT embed a concrete domain alias. Sandbox
script failure, resource limit, and runtime unavailability SHALL remain
distinct controlled categories from static source-envelope rejection.

#### Scenario: Long feasible message does not consume a repair

- **WHEN** an otherwise-valid achievable result contains a non-blank display
  name longer than 80 characters
- **THEN** the application normalizes it to a bounded display name
- **AND** the feasibility attempt may succeed without another generation.

#### Scenario: Render reads a failed outcome payload

- **WHEN** the render function fails the synthetic failed-outcome fixture
  before returning a presentation
- **THEN** validation reports the controlled failed-outcome-handling category
- **AND** Retry receives targeted checked-in guidance to branch on status before
  reading payload.

#### Scenario: Render request is preflighted before model load

- **WHEN** the application constructs a render request using the longest valid
  algorithm alias
- **THEN** the stage objective remains within its checked-in text limit
- **AND** exact node shapes remain present in the bounded render context
- **AND** any local request-construction failure is persisted before device
  recovery or model load and is not reported as runtime unavailability.

#### Scenario: Generic render contract binds an execution alias

- **WHEN** the render request is built for any accepted tool-call algorithm
- **THEN** its generic API contract contains no concrete alias example
- **AND** the exact allowed aliases are supplied separately in `toolResults`
- **AND** the contract requires `outcomes[alias]` rather than `outcomes.get()`.

### Requirement: Private resumable authoring data

Persisted authoring instructions, candidates, rejected artifacts, validation
results, and checkpoints SHALL remain in app-private local storage, SHALL be
excluded from backup, normal Chat history, execution logs, analytics, and
automatic upload, and SHALL obey checked-in size limits. Tool credentials and
provider results SHALL NOT enter persisted authoring stage data.

Final widget confirmation SHALL remain a separate explicit action after current
checkpoints pass exact assembly validation and preview. Confirming SHALL persist
the normalized widget revision and remove the transient authoring session;
discarding SHALL remove the transient session without creating or changing a
widget.

#### Scenario: Workflow approval is not widget consent

- **WHEN** the user accepts a stage candidate or continues to the next stage
- **THEN** no widget is installed, enabled, scheduled, or granted capabilities
- **AND** final preview and confirmation remain required.

#### Scenario: Confirm and remove transient state

- **WHEN** the user confirms a fully validated preview
- **THEN** the application atomically persists the normalized widget revision
  and consent record
- **AND** removes the transient session, candidates, rejected artifacts, and
  attempt history.

#### Scenario: Discard and remove transient state

- **WHEN** the user discards an inactive authoring session
- **THEN** all of its persisted transient content is removed
- **AND** no confirmed widget revision is created or modified.

### Requirement: Notification actions preserve current authoring foreground ownership

A widget-authoring notification action SHALL change only the authoring session
revision it names. Delivery of a stale or duplicate action SHALL NOT end
foreground execution or remove the ongoing notification for a different active
attempt. Foreground execution SHALL end only when the latest attempt has a
committed non-active outcome.

#### Scenario: Stale cancel arrives during a newer attempt

- **GIVEN** a queued, deferred, or running attempt is active at a newer session
  revision
- **WHEN** a Cancel action from an older revision is delivered
- **THEN** the active attempt and its foreground notification remain intact
- **AND** the current attempt remains cancellable from the UI or notification.

#### Scenario: Stale continue or retry arrives during a newer attempt

- **GIVEN** a queued, deferred, or running attempt is active at a newer session
  revision
- **WHEN** a Continue or Retry action from an older revision is delivered
- **THEN** it starts no generation and changes no checkpoint
- **AND** it does not stop foreground execution for the current attempt.

#### Scenario: Matching cancel completes

- **GIVEN** the notification names the current active attempt
- **WHEN** the user requests Cancel
- **THEN** cancellation is requested for that attempt only
- **AND** the foreground notification remains until the cancellation outcome
  is committed
- **AND** foreground execution then ends and the durable retry/review state is
  presented according to the existing authoring workflow.

#### Scenario: Delayed action follows a newer foreground start

- **GIVEN** a newer attempt has requested foreground execution
- **WHEN** an older action is delivered after that start request
- **THEN** the service acknowledges any pending foreground-start contract
- **AND** evaluates its lifetime using the latest authoring state rather than
  stopping solely because the action is stale.
