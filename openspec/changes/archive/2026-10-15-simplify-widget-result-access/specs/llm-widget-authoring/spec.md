# Spec Delta

## ADDED Requirements

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
