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

That metadata declares the authoring operation as `create` or `edit`; the
instruction itself describes desired behavior/content and need not repeat the
UI operation or even the word "widget". The program API also translates
natural random/varied/shuffled selection language, including common supported
user-language equivalents, into the `seed` grant and
`runtime.seededIndex(length)`. It explains that `Math.random` is unavailable,
fresh executions may vary, and the same runtime snapshot reproduces the same
index. This keeps implementation policy in application-owned context while
allowing ordinary user phrasing.

The feasibility stage receives a small choose-first decision contract in
addition to its capture schema. The model returns exactly four fields:
`outcome`, one outcome-dependent `message`, `periodicIntervalHours`, and the
minimum registered `toolIds`. The application maps the message to a display
name, reason, or clarification question and derives protocol metadata,
enablement, registered tool versions, deterministic runtime grants,
allowlisted presentation grants, and terminal normalization. This reduces
mechanical schema work for a small local model without transferring authority
to it. Later stages continue to preserve the resulting validated envelope
exactly.

The base pipeline is:

1. **Feasibility and tool selection.** Return `achievable`, `unachievable`, or
   `needs_clarification`, plus one bounded message, bounded schedule intent, and
   the minimum registered tool IDs. The application resolves eligible versions
   and derives the remaining capability envelope from checked-in policy. It
   rejects invented or ineligible tools and treats the model's feasibility
   judgment as an untrusted proposal. A request that needs an unavailable
   operation or a later live-result-dependent call ends without code generation
   or mutation.
2. **Typed algorithm.** For an achievable request, return a bounded acyclic IR
   of runtime inputs, independent planned tool calls, transformations, and one
   presentation objective. Step IDs and dependencies are explicit. Tool-call
   arguments may depend on immutable runtime/state inputs but not on live
   provider results because confirmed v1 execution retains the existing
   plan-all, dispatch, then render boundary.

   Runtime authority is not represented by model-authored per-step fields.
   The application derives it once from the validated feasibility envelope and
   supplies that runtime API to later code stages. The algorithm schema omits
   `runtimeInputs`; the parser accepts and discards that field only as legacy
   wire compatibility. The model selects only a frozen tool ID for a tool-call
   step; the application resolves its exact frozen version. Legacy
   `contractVersion` values are ignored, and non-tool steps omit tool metadata.
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
  later call. The shared wire step shape carries nullable tool metadata for all
  kinds, but only `tool_call` consumes it as authority. Runtime-input and
  transform steps normalize those values away because they cannot invoke a
  tool; unknown fields and every real tool identity/version remain strict;
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
Each controlled failure code also maps to a checked-in actionable correction
instruction. In particular, an outcome-contract failure repeats the three
feasibility outcome invariants, while a missing artifact explicitly requires
one capture-tool call. The model still receives no arbitrary validator message,
exception, provider content, or additional authority.

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

Every model generation after the first feasibility attempt is an explicit
native-lifecycle recovery boundary, including repairs. The application closes
the selected LiteRT-LM model, leaves the engine unloaded while a bounded
cancelable gate samples temperature and memory pressure, and reloads the same
fixed authoring configuration and text-only workload only after the gate is
stable. The Android gate requires three consecutive five-second samples with
thermal status `NONE`, battery temperature at or below 32 C, process PSS at or
below 1 GiB, at least 20 percent system memory available, and no system
low-memory signal. Unknown platform signals do not block the remaining known
signals. A boundary times out after three minutes and leaves the model unloaded.

Unload, wait, and replacement publication are serialized with other engine
ownership changes. Native unload/load ownership transfer completes
non-cancellably, while the cooling wait remains cancellable so navigation does
not keep a hidden authoring attempt alive. The UI and sanitized runtime
telemetry expose unload/wait/reload as distinct phases. Terminal `unachievable`
and `needs_clarification` decisions return without paying another recovery
cost. This diagnostic-first policy intentionally trades substantial latency for
a controlled test of the cumulative memory, swap, conversation-creation, and
thermal degradation observed physically; model eligibility remains disabled
until the complete pipeline succeeds under this policy.

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
native runtime. The matrix never persists or uploads a report. Suite v10 adds
three independent single-generation feasibility probes: full context with the
natural request, compact feasibility-only context with that request, and
compact context with an explicit implementation control. Each is selected and
reported independently so a physical comparison can force-stop the process
between probes instead of thermally priming the production case with the schema
matrix. The compact context retains operation semantics, supported schedule,
runtime and presentation values, registered tool identities and field names,
random-selection guidance, and current-widget metadata without source, while
omitting later-stage examples and full nested schemas. Copy/share results
contain only controlled outcome codes, durations, whether
and how many tool callbacks were observed, per-tool aggregate argument byte
counts, controlled complete-pipeline failure stage/code when applicable, and
device/app/model/schema hashes. Probe results additionally contain UTF-8 byte
sizes for system instruction, user text, context, and schema; estimated input
characters; a context SHA-256; and monotonic first-event, tool-capture,
terminal, watchdog, return, and cleanup-overrun timings. Feasibility rejection
is localized to bounded
JSON/root, exact field set, protocol, display-name, enabled, schedule, tool
selection, runtime grant, presentation grant, or outcome-contract categories.
The same category scopes a bounded repair. The report includes the controlled
stage/code, attempt number, and argument byte count for each failed attempt so
repair progression remains observable without the rejected value. Debug builds
emit those already-sanitized lines under one Logcat tag for terminal-only ADB
collection. The stage/code fields are null for successful or non-stage terminal
outcomes. Prompts, generated arguments, JavaScript, raw model output, exception
messages, and stack traces are excluded from both report and controlled Logcat.
This distinguishes schema/protocol stages without creating a remote diagnostic
data path or exposing the native parser's potentially content-bearing error.

Suite v12 retains the bounded compact context promoted in suite v11 and replaces
the feasibility wire artifact with the four-field decision above. The algorithm
and source-fragment stages still receive their full stage-specific validated
inputs. `complete_pipeline_compact_natural` remains one cold, production-like
pipeline run with the natural request and no preceding schema matrix or probe.
This separates end-to-end model behavior from thermal and runtime state
accumulated by characterization generations; it remains diagnostic evidence and
does not bypass the complete eligibility matrix.

Suite v13 adds `algorithm_natural`, a separate one-generation algorithm probe.
The application supplies a normalized achievable feasibility fixture derived
from its registered Wikipedia capability, then builds the same natural prompt,
algorithm context, schema, capture, and semantic validation used by production.
The probe cannot call a provider and reports only controlled outcome, aggregate
capture metadata, sanitized input metrics, and lifecycle timings. Complete
pipeline modes also record one lifecycle entry per stage-local attempt: repair
flag, controlled outcome, first generation event, tool capture, terminal event,
watchdog, return, and cleanup overrun. Together these signals distinguish a
native generation stall, no callback, transport/parser rejection, and captured
but structurally invalid output without placing raw content in the normal report
or Logcat.

Debug LiteRT-LM sessions additionally emit sanitized native lifecycle telemetry
under the existing runtime Logcat tag. Process-local request/resource IDs
correlate engine initialization or reuse, conversation create/reuse, request
submission, first callback, terminal callback, cancellation, close, and final
cleanup. Every event records the current active-generation count and, when
available, process thread count, RSS, swap, and Android thermal status. An
explicit warning event is emitted when more than one native generation is
active. This telemetry contains no model ID/path, prompt, generated content,
tool arguments, exception messages, provider data, or stack trace, and is not
persisted or uploaded. It distinguishes model reload from conversation churn,
concurrent consumers, resource pressure, and blocking native cleanup without
changing authoring behavior.

Discarded native conversation wrappers are tracked by weak object identity.
This retains exactly-once cancel/close semantics while a wrapper is reachable,
without turning duplicate-disposal protection into a process-lifetime strong
reference registry. Debug lifecycle snapshots include total/native PSS,
native/Java heap, available system memory, and low-memory state in addition to
RSS, swap, threads, and thermal status, so close-time release can be compared
without recording model or user content.

For diagnosing model/schema mismatches that controlled codes cannot identify,
debug builds may retain an in-memory raw trace only for the user-initiated
diagnostic run. The normal report and Logcat remain sanitized. After a separate
warning and explicit confirmation, the UI may write a replace-on-export JSON
sidecar under the application cache and share it through a read-only
`FileProvider` grant. The sidecar preserves the exact stage instruction,
effective context, capture schema, and callback argument string for each round;
rounds with no callback remain empty rather than inventing native output. The
control is absent in release builds, the file is excluded from backup, and no
automatic upload or telemetry path is added.

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
