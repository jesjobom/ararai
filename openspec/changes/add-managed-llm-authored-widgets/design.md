## Context

This change depends on the implemented contracts and automated validation from
`add-sandboxed-javascript-widget-runtime`. That foundation validates an
immutable `{manifest, source}` package, executes planning and presentation in
isolated data-only phases, and can call fake `WIDGET` tools through the existing
gateway. ArarAI still has no durable widget data, production widget-eligible
tool, scheduler, renderer, management surface, or model authorship workflow.

The app uses plain SQLite, application-owned controller composition, Compose,
WorkManager for bounded durable work, a static model catalog, and model-specific
tool capability declarations. Current Wikipedia search is model-only and its
canonical result is optimized for model synthesis rather than direct UI data.
See `proposal.md` for motivation and the five delta specifications for the
observable contracts.

## Goals / Non-Goals

**Goals:**

- Deliver one complete internal-widget lifecycle without turning the runtime
  into a downloadable plugin or arbitrary network platform.
- Keep authorship, lifecycle, execution, persistence, rendering, and registered
  tool transport as distinct boundaries.
- Decompose authorship into independently validated model stages so a small
  local model never has to select tools, design behavior, generate every
  function, and satisfy the final package contract in one response.
- Make every persisted or scheduled behavior traceable to a validated immutable
  revision and explicit user consent.
- Run refreshes without a model and retain useful last-known content when a
  provider or program fails.
- Exercise dynamic date logic and program generation with a real structured
  Wikipedia source.

**Non-Goals:**

- Android launcher/app widgets, WebViews, generated Compose, remote sync, or
  public widget sharing/import.
- Arbitrary endpoints, user-supplied provider implementations, downloaded
  JavaScript packages, or direct JavaScript network/storage access.
- Currency, weather, or other new providers; financial decimal/history
  semantics remain later work.
- Cross-widget reads, widget-to-widget triggers, notifications, exact wall-clock
  scheduling, unattended repair, unbounded repair loops, or model inference
  during confirmed widget execution.
- Adaptive execution in which a later tool request depends on an earlier live
  provider result. V1 programs may plan multiple bounded calls, but all call
  arguments are derived before dispatch from runtime/state inputs.
- Persisting authoring as a conversation or allowing a model/widget to choose
  arbitrary Wikipedia endpoints, response shapes, or transport policy.

## Decisions

### Make the managed definition the lifecycle control plane

`ManagedWidgetDefinition` is separate from `WidgetProgramManifest`. It owns the
stable UUID, user-visible name, enabled state, supported periodic schedule,
active immutable revision, consent digest, created/updated timestamps, and
current status. Program revisions own the normalized manifest, exact source,
digest, and creation time. The application recomputes IDs, revision numbers,
digests, grants, and hard limits; values proposed by a model never become
authoritative.

This split lets the scheduler and manager inspect a widget without parsing or
executing JavaScript and lets one stable widget survive multiple program
revisions. Embedding schedule and ownership in the runtime manifest was
rejected because it would couple the sandbox contract to Android lifecycle and
allow generated source to influence management authority.

The consent digest covers the normalized active program digest, effective
capability/tool grant, retained-history scope, allowed actions, and schedule.
Any edit creates a full candidate revision. Only one user confirmation
transaction installs it and replaces work. Previous revisions remain immutable
for bounded audit/rollback visibility, but this change does not expose an
automatic rollback action.

### Store widget data in a separate bounded SQLite database

Use a dedicated widget database rather than extending the Chat database:

- `managed_widgets`: definition, active revision, consent, schedule, state, and
  last successful/attempt timestamps;
- `widget_program_revisions`: immutable manifest/source/digest and revision
  metadata;
- `widget_presentations`: at most the current validated cached presentation per
  widget/revision;
- `widget_runs`: bounded status, timing, revision, tool IDs, and controlled
  failure codes;
- `widget_observations`: bounded typed values keyed by the owning widget,
  observation name, and timestamp.

Foreign keys and transactions prevent orphan revisions and partial activation.
Repository operations run off the main thread. The store has explicit schema
versions and deterministic forward migrations; an unsupported schema fails
closed while leaving the file intact. Count and age ceilings are checked in so
cleanup is predictable. Source, results, and observations remain app-private;
Android backup is already disabled and no export path is added.

A separate database reduces migration risk to conversations and makes complete
widget deletion testable. Preferences were rejected because source, revisions,
history, and transactional activation do not fit key/value storage. Room was
not introduced because the project already uses a small explicit SQLite
boundary and this feature does not justify a second persistence framework.

### Use one unique WorkManager schedule and one execution lease per widget

The scheduler maps the stable widget UUID to one deterministic unique periodic
work name and replaces that work only after a confirmed definition transaction.
Supported v1 intervals are one hour or longer, with 24 hours as the suggested
Wikipedia default. UI copy states that Android chooses an inexact execution
window. Network constraints are derived from the confirmed tool plan/capability
set rather than chosen by JavaScript.

The worker loads and revalidates the active revision and consent at start. A
repository-backed run lease keyed by widget ID prevents overlap between
periodic and manual work across process/controller recreation. Execution is:

1. acquire the current-definition lease and record a run attempt;
2. validate revision, runtime API, grant, and referenced contracts;
3. execute `plan`, validate the whole plan, and dispatch tool calls;
4. execute `render` with canonical outcomes and owned bounded observations;
5. in one transaction, store the controlled run outcome, replace a valid cache,
   append valid observations, and release the lease.

The worker does not construct a model engine and does not use an immediate
automatic retry. The next periodic run or an explicit user refresh is the next
attempt. A failed run preserves the last valid presentation with stale/error
metadata. Revision/consent checks before the final transaction discard late
results after edit, disable, or deletion.

### Build dynamic source through a bounded multi-round authoring pipeline

Replace the one-shot proposal with an application-owned state machine whose
model outputs are small, typed, and independently validated. The selected model
must declare the versioned `widget_authoring_pipeline_v1` catalog capability;
the historical `propose_widget` authoring-tool marker does not imply support for
the replacement protocol. Every stage runs in a fresh ephemeral LiteRT-LM
conversation that advertises exactly one
capture-only stage tool and no normal Chat, knowledge, calculator, provider, or
widget-execution tool. The engine workload may stay loaded across stages, but
conversation state is never reused implicitly; each stage receives only the
original instruction, validated artifacts it needs, and bounded non-secret API
metadata.

The base pipeline is:

1. **Feasibility and capability selection.** Return `achievable`,
   `unachievable`, or `needs_clarification`, plus bounded display/schedule intent,
   required registered tool/version IDs, runtime values, presentation features,
   and a controlled explanation. The application rejects invented or
   ineligible capabilities and treats the model's feasibility judgment as an
   untrusted proposal. A request that needs an unavailable operation or a later
   live-result-dependent call ends without code generation or mutation.
2. **Typed algorithm.** For an achievable request, return a bounded acyclic IR
   of runtime inputs, independent planned tool calls, transformations, and one
   presentation objective. Step IDs and dependencies are explicit. Tool-call
   arguments may depend on immutable runtime/state inputs but not on live
   provider results because confirmed v1 execution retains the existing
   plan-all, dispatch, then render boundary.
3. **Tool-call functions.** Generate one bounded named JavaScript function per
   planned call. A function receives only the versioned runtime stdlib snapshot
   and explicitly declared inputs and returns one semantic call object for its
   fixed registered tool/version. The application parses and executes each
   fragment against synthetic normal and boundary inputs, then validates its
   output against the registry contract before accepting it.
4. **Orchestration and presentation.** Generate a short `plan` orchestrator over
   the immutable accepted call-function signatures and a separate `render`
   function over typed synthetic outcomes. Validate the orchestrator's complete
   plan, the renderer's success/failure/empty-state trees, result provenance,
   requested presentation capabilities, and resource bounds independently.
5. **Deterministic assembly and dry-run.** The application concatenates the
   exact accepted model-generated fragments in a fixed order; no final model
   round may rewrite them. It builds the manifest/digests and dry-runs the exact
   assembled package with a fresh runtime snapshot and synthetic tool outcomes.
   Production tools and providers are never invoked during authorship.

The v1 capture tools are `submit_widget_feasibility`,
`submit_widget_algorithm`, `submit_widget_call_function`,
`submit_widget_plan_function`, and `submit_widget_render_function`. A repair
round reuses the rejected stage's tool/schema with explicit repair context; it
does not introduce a generic source-replacement tool. Their bounded artifacts
are:

- `WidgetFeasibilityArtifact`: protocol version, outcome, display name,
  enablement/schedule intent, selected `{id, version, purpose}` tools, runtime
  values, presentation capabilities, and an optional controlled reason or one
  clarification question;
- `WidgetAlgorithmArtifact`: protocol version and ordered steps with unique ID,
  kind, objective, dependency IDs, and fixed tool/version for tool-call steps;
  dependencies must be acyclic and cannot bind a live provider result into a
  later call;
- `WidgetSourceFragmentArtifact`: protocol version, application-assigned
  artifact/function identity, declared input names, and one bounded function
  source. The stage prompt fixes the signature, responsibility, tool/version,
  and return contract; the model cannot rename or add entrypoints;
- `WidgetAssembly`: application-owned ordered accepted fragment bytes, frozen
  capability envelope, fixed resource policy, normalized manifest, and digests.
  This is computed locally and is never a model-proposed control artifact.

Every structured object rejects missing or additional fields. Free-text
objectives, purposes, explanations, and questions have short character limits
and are display/context only; they never become code, grants, identifiers, or
transport parameters without a later typed stage and application validation.

All stage schemas, artifact sizes, selected calls, generation count, per-stage
deadlines, and total foreground deadline are bounded by checked-in policy. The
normal path takes between four and eight sequential model generations for the
v1 maximum of four tool calls. At most two repair generations are allowed for
the entire attempt. A repair prompt receives only the rejected stage artifact,
its immutable dependencies, the relevant contract, and a controlled validation
code; it cannot broaden tools, capabilities, schedule, or presentation scope.
Exhaustion ends the attempt without mutation. This bounded explicit-foreground
repair supersedes the earlier rejection of all automatic repair loops; ongoing,
background, or open-ended repair remains forbidden.

The UI presents the state machine directly: analyzing feasibility, selecting
tools, designing the algorithm, generating call N of M, generating
orchestration, generating presentation, validating, repairing stage X with its
bounded attempt count, and draft ready. Cancellation or navigation away cancels
the active generation, releases every ephemeral conversation, clears all
in-memory artifacts, and discards late callbacks. Intermediate prompts,
artifacts, generated source, validation detail, and stage history are not
persisted, logged, reported, exported, or added to Chat.

The isolated authoring workload uses a fixed 4,096-token context budget and a
temperature no higher than 0.2 without changing the saved Chat preference.
Smaller per-stage contexts may be used only when a device-validated policy
explicitly declares them; the complete stage input plus bounded output reserve
must fit before generation starts. Physical evidence from the one-shot flow
showed that 2,048 tokens caused reproducible no-call failures while 4,096
restored the E4B structured transport.

The deterministic runtime standard library presents fresh execution-time
values through an immutable API such as `runtime.currentLocalDateTime()` and
`runtime.language`. Kotlin constructs the snapshot anew for every manual,
background, preview, and dry-run execution. The helper does not read a QuickJS
or host wall clock, and the sandbox does not expose `Date.now()`, ambient
randomness, Android/JVM objects, or callbacks. Therefore generated functions
can discover the current local date on every widget run without baking an
authoring-time date into source or sacrificing replay equivalence.

After assembly, the draft pipeline recomputes the runtime manifest and program
digest, validates source and static grants, and executes the complete
side-effect-free plan/render dry-run. It then shows exact source plus normalized
behavior, schedule, network, storage, action, and capability disclosures.
Editing supplies the current normalized definition and source to the relevant
stages, shows semantic permission/schedule and source diffs, and still requires
confirmation with authority expansion emphasized.

Free-form model responses remain rejected because every artifact needs an
unambiguous boundary. Allowing the model to assign widget IDs, revisions,
digests, resource limits, consent, activation, runtime helpers, or assembly
order remains forbidden because those are application control-plane facts.

For physical characterization without ADB, the authoring surface also provides
an explicit local diagnostic for the selected eligible model. The existing
suite-v2 matrix remains useful as historical evidence for the one-shot
`propose_widget` transport, but it is not acceptance evidence for generated
program quality. The revised diagnostic characterizes every stage-specific
capture schema and then runs the complete pipeline against synthetic runtime
and tool-result fixtures without production provider access.
It aborts after a callback timeout rather than reusing a potentially wedged
native runtime. The matrix never
persists or uploads a report. Suite v4 copy/share results contain only controlled
outcome codes, durations, whether and how many tool callbacks were observed,
per-tool aggregate argument byte counts, controlled complete-pipeline failure
stage/code when applicable, and device/app/model/schema hashes. The stage/code
fields are null for successful or non-stage terminal outcomes. Prompts,
generated arguments, JavaScript, raw model output, exception messages, and stack
traces are excluded.
This distinguishes schema/protocol stages without creating a remote diagnostic
data path or exposing the native parser's potentially content-bearing error.

The historical suite-v2 result is model- and inference-budget-specific. E4B at
4,096 context tokens completed all five cases, including the full production
schema and context. E2B completed the first four
but reproducibly failed the combined production case in LiteRT-LM parsing before
the application tool callback. Therefore only E4B declares `propose_widget` in
the checked-in catalog for this version. E2B keeps its independent Chat, media,
reasoning, and normal-tool capabilities. A model-specific alternate proposal
protocol was rejected for v1 because the flat characterization case omits part
of the production authority surface and does not establish end-to-end proposal
quality; adding a second protocol would increase validation and compatibility
surface without sufficient evidence. Later physical E4B attempts passed that
transport matrix but produced `InvalidToolArguments` and `InvalidPlan` for real
source, including with an explicit control prompt. Those failures invalidate
one-shot semantic acceptance and motivate the staged protocol. E2B and E4B
eligibility for the replacement pipeline must be characterized independently;
neither is assumed from the old matrix.

### Add distinct dual-consumer structured Wikipedia tools

Register `wikipedia_pages@1` as both `MODEL`- and `WIDGET`-eligible. It
accepts only `{query, language}` and returns a discriminated bounded result with
up to the existing maximum number of `{title, extract, canonicalUrl, language,
retrievedAtMillis}` pages. It reuses the current URLConnection transport,
language-specific Wikipedia host allowlist, timeouts, response/field limits,
redirect policy, UTF-8 checks, and enablement state, but uses a typed page result
rather than reconstructing extracts from narrative model context.

Add `wikipedia_on_this_day@1`, also eligible for both consumers. It accepts only
`{month, day, language}`, validates a real calendar day, and calls the fixed
official language-specific Wikipedia REST feed path for historical events. The
application parses the untrusted response into a bounded list of
`{year, text, title, canonicalUrl}` events plus language and retrieval time.
Only plain text and canonical article links cross the tool boundary. Widget
execution receives the complete bounded list so a program can select by its
supplied seed; the model adapter projects at most three events and matching
sources to fit the checked-in local models' short context windows.

The legacy `wikipedia_search@1` binding remains registered for compatibility,
but checked-in models advertise the structured pair. Tool descriptions and the
turn instruction tell models to use `wikipedia_on_this_day` only for historical
events on an explicit calendar date and `wikipedia_pages` for direct stable
encyclopedic lookup. Current news, comparisons, recommendations, broad research,
and multi-source evidence remain `web_search` work when available. Both
Wikipedia tools share one per-turn invocation budget and one user enablement
preference.

The reference creation prompt asks for an event on the current local day/month.
A valid generated program derives numeric month/day from the explicit runtime
date, calls `wikipedia_on_this_day`, selects one bounded event using the run's
supplied seed, and returns a card with year, event text, and a canonical link.
This is an acceptance scenario, not a hard-coded built-in widget; the feature
remains LLM-authored.

### Render a validated intermediate tree in a focused Compose surface

Add a dedicated Widgets route, controller/ViewModel, list screen, detail screen,
authoring flow, draft preview, and run-history view. Keep these in focused
widget/UI files and inject narrow services from the application composition
root instead of adding full screens to `ArarAiApp.kt`.

The renderer maps only the runtime's versioned intermediate nodes and semantic
tokens to fixed accessible Compose components. It never evaluates source.
Validated canonical Wikipedia links become explicit user-click actions through
the application URI opener; no widget can create an arbitrary intent. Rendering
errors show controlled state and leave the stored program inspectable.

### Keep logs and observations distinct

Run logs answer operational questions: when, which revision, duration, planned
tool IDs, outcome code, and sanitized message. Observations are typed program
data intentionally emitted under declared keys for future comparisons. They
have different quotas and retention, and neither can read another widget's
rows. JavaScript sees only a bounded projection prepared for its own active run;
it never receives a database handle or raw SQL capability.

Using execution logs as time-series state was rejected because diagnostics can
be sanitized or evicted independently and should not become a hidden program
API. The first Wikipedia flow need not emit observations, but the storage and
capability boundary are established and tested for future stateful widgets.

## Risks / Trade-offs

- [Local models generate invalid or unsafe source too often] → Gate models by a
  versioned authoring-pipeline capability; split feasibility, typed algorithm,
  call functions, orchestration, and rendering into independently validated
  stages; assemble accepted fragments without rewriting; allow at most two
  targeted repairs; and measure Portuguese and English creation/edit cases on
  physical devices.
- [Multi-round authoring is too slow or memory-intensive] → Reuse only the
  loaded model workload, keep conversations isolated, bound every stage and the
  total deadline, expose progress/cancellation, and reject a stage whose input
  plus output reserve cannot fit its context budget.
- [A repair broadens authority or silently changes accepted behavior] → Bind
  every repair to the frozen feasibility/algorithm artifacts, expose only a
  controlled failure code, reject capability/schedule expansion, and preserve
  accepted fragments byte-for-byte.
- [The second change becomes too broad] → Keep two narrow Wikipedia contracts
  and one create/edit/list/detail vertical slice; defer other providers, sharing,
  launcher widgets, notifications, and agentic refresh.
- [WorkManager timing differs from the requested instant] → Support interval
  semantics, disclose inexact timing, timestamp every result, and let history
  lookups use explicit tolerance rather than assuming exact execution.
- [A revision changes while work is active] → Bind the run to revision and
  consent digests and compare them transactionally before storing results.
- [Persisted source or provider text leaks through diagnostics] → Store source
  only in revision rows, use allowlisted log fields/codes, cap all data, and test
  that prompts, credentials, headers, stack traces, and raw responses are absent.
- [Wikipedia content contains instructions or hostile markup] → Parse only
  bounded plain-text fields and canonical links, treat all fields as untrusted
  data, and render them without HTML or code interpretation.
- [Frequent background inference drains battery] → Never run a model in workers,
  enforce a one-hour minimum, derive network constraints, and expose disable and
  refresh status prominently.
- [A database migration strands existing widgets] → Version the schema,
  transactionally migrate, fail closed without destructive fallback, and keep
  creation disabled until storage is healthy.

## Migration Plan

1. Require the sandboxed JavaScript runtime implementation and automated gates
   to be complete, capture its checked-in runtime/API version, and provide the
   optimized self-service physical validator before starting source
   implementation. Keep production widget activation, release acceptance, and
   both changes' archival blocked until the physical arm64 report passes.
2. Add the widget SQLite schema/repository and lifecycle tests with no
   navigation or workers; existing installs create an empty database lazily.
3. Add the structured Wikipedia page and on-this-day bindings plus deterministic
   fake-transport tests, then expose both through explicit model and widget
   eligibility with clear selection instructions.
4. Add run coordination, unique scheduling, cache/observation/log transactions,
   and worker tests using fake runtime and tools.
5. Replace the one-shot authoring adapter with the staged coordinator, closed
   intermediate schemas, deterministic runtime stdlib, fragment validators,
   fixed assembler, synthetic dry-run, and bounded repair policy. Keep all
   intermediate state ephemeral and production activation behind confirmation.
6. Update the Compose authoring surface with stage progress, bounded repair
   status, cancellation, unreachable/clarification outcomes, and the existing
   final preview/diff/confirmation flow.
7. Characterize the complete replacement protocol on eligible physical models,
   then validate the Wikipedia reference prompt, edits, negative outcomes,
   manual/background refresh, lifecycle recovery, cancellation, and deletion on
   arm64 in addition to the automated quality gate.

Rollback removes navigation, workers, production widget tool registration, and
authoring composition first. Existing widget work is cancelled before removing
the runtime caller. The app may leave the private widget database untouched for
forward recovery; destructive deletion is not required for rollback and normal
Chat data is unaffected.
