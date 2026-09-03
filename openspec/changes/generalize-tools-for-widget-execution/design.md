## Context

The current `ApplicationTool<Request, Result>` boundary describes display name,
category, and execution, and `KnowledgeTool` specializes it for external
evidence. Discovery, enablement, provider readiness, argument parsing, result
serialization, per-turn limits, and LiteRT-LM adaptation are still composed in
several tool-specific paths. Calculator is adapted directly from its local math
engine, while Wikipedia and web search use knowledge-tool implementations under
their OpenAPI adapters.

This works for Chat and Voice Chat but does not provide one application-level
way for a future widget runtime to discover and invoke a tool without passing
through model-specific classes. Existing encrypted provider configuration,
model capability metadata, source provenance, and tool-call limits must remain
authoritative. See `proposal.md` for motivation and the delta specifications for
the observable contract.

## Goals / Non-Goals

**Goals:**

- Make application tool identity, contracts, configuration readiness, and
  execution discoverable from one composition root.
- Let model and widget consumers share domain executors while applying
  independent eligibility and consumer-specific policies.
- Keep the shared path suspending, bounded, cancellable, deterministic under
  fakes, and free of LiteRT-LM types.
- Preserve all current user-visible tool behavior during migration.
- Establish a narrow widget-domain seam that a later widget runtime and worker
  can call.

**Non-Goals:**

- A dynamic plugin system, downloadable tools, reflection-based discovery, or
  runtime code loading.
- Model-generated Kotlin, JavaScript, SQL, expressions, callbacks, URLs, or
  other executable programs.
- A widget definition language, generated layouts, data binding, persistence,
  editing, listing, deletion, enablement, scheduling, WorkManager integration,
  cache, or stale-data policy.
- Adding quote, weather, or any other provider.
- Making existing narrative/evidence results suitable for widgets without a
  separately designed structured output contract.

## Decisions

### Register bindings, not only executor instances

Add an application-domain registry whose entries bind:

- stable tool ID and contract version;
- display metadata and category;
- independently eligible consumer surfaces;
- bounded argument and canonical result codecs/schemas;
- a user-configuration/readiness resolver;
- a suspending domain executor; and
- non-secret execution-policy metadata needed by dispatch.

A bare map of executor lambdas was rejected because it cannot validate persisted
versioned invocations, explain readiness, or prevent a future widget consumer
from invoking a model-only tool. Reflection or annotation scanning was rejected
because the app has a small checked-in catalog and benefits from deterministic,
compile-time-visible composition.

Registry construction fails for duplicate ID/version pairs, empty consumer
sets, or invalid contracts. The application composition root constructs the
registry explicitly; this does not justify adding a general dependency-injection
framework.

### Resolve availability in the requesting consumer's context

There is no single global `available` boolean. Resolution combines distinct
facts:

1. the ID/version is registered;
2. the user enabled the tool or its configured provider chain;
3. required credentials and verification state are ready;
4. the entry declares the requesting consumer;
5. consumer-specific policy accepts the invocation; and
6. for `MODEL` only, the selected installed model declares verified capability.

The consumer set allows `MODEL`, `WIDGET`, or both. Widget resolution never
reads the selected model or its capability metadata. Model resolution continues
to use the current catalog tool names and native-conversation compatibility
rules. This is preferred over defining separate model-tool and widget-tool
catalogs, which would duplicate identity, configuration, and executor wiring.

Existing tools are registered as `MODEL`-eligible during this change. No
existing tool is marked `WIDGET`-eligible until it has a bounded structured
result contract suitable for direct presentation. Deterministic registry and
gateway tests use local fake model-only, widget-only, and dual-consumer tools to
prove all eligibility combinations without implying a shipped widget feature.

### Keep typed domain execution behind serialized boundary adapters

Tool implementations continue to receive typed semantic requests and return
typed domain results. Each registry binding owns the codecs that translate a
bounded serialized invocation into that request and the canonical result into a
bounded structured response. The shared dispatcher performs identity, version,
surface, readiness, and schema checks before invoking the typed executor once.

This retains useful Kotlin type safety while allowing a future persisted widget
plan to refer to stable serialized contracts. Passing arbitrary JSON directly
to every executor was rejected because it repeats parsing and validation and
weakens type safety. Exposing Kotlin generic types directly to persisted widget
plans was rejected because those types are not a stable storage contract.

Widget eligibility requires a structured, bounded canonical result codec.
Model-only evidence tools may continue to produce domain content optimized for
model synthesis; registration alone does not claim that free-form reference
text is widget data.

### Keep consumer protocol and lifecycle in adapters

The registry and dispatcher do not depend on LiteRT-LM. A model adapter remains
responsible for the current OpenAPI schema string, synchronous SDK bridge,
per-turn call counter, lifecycle events, model-facing result framing, source
capture, and continuation behavior. It delegates the actual validated semantic
execution to the shared dispatcher.

The widget gateway is a suspending widget-domain adapter. It supplies a
`WIDGET` execution context, receives a bounded invocation from a future trusted
widget-plan caller, and returns the canonical structured result. It does not use
`runBlocking`, construct an OpenAPI adapter, load a model, or own retries and
scheduling. A later worker can add its own scheduling, retry, and cache policy
without changing tool implementations.

Consumer-specific invocation ceilings remain outside the executor. Existing
model per-turn limits therefore remain unchanged, while future widget quotas
can be specified with the widget lifecycle instead of inheriting conversational
semantics accidentally.

### Adapt existing configuration stores instead of migrating secrets

Expose a small configuration/readiness contract from the registry entry and
adapt the current local preference and encrypted credential stores to it.
Wikipedia and calculator map their persisted enabled preferences. The stable
`web_search` tool maps readiness to the current enabled, verified Exa/Tavily
provider chain while provider priority and fallback remain inside the existing
web-search composition.

Credentials are resolved only inside application-owned provider construction or
execution. They never enter registry descriptors, serialized invocation
arguments, widget plans, result envelopes, or consumer-visible diagnostics. A
new generic credential vault or persistence migration was rejected because it
adds risk without being required for shared execution.

### Use stable controlled dispatch failures

The shared boundary returns consumer-neutral failures for unknown tool,
unsupported version, disabled tool, not configured, ineligible consumer,
invalid arguments, cancellation, timeout, and internal unavailability. Tool-
specific domain failures remain available behind the canonical result adapter
where needed.

Model adapters translate shared failures into their current model-visible
failure codes and lifecycle events. The widget gateway returns the bounded
canonical failure without asking a model to interpret or repair it. Exception
details and secrets do not cross either boundary.

## Risks / Trade-offs

- [A registry becomes a service locator] → Restrict it to immutable tool
  metadata, resolution, and dispatch; inject narrow consumer gateways into
  controllers instead of exposing the registry throughout the UI.
- [Generic schemas erase domain guarantees] → Keep typed request/result
  executors and locate serialization in tested per-tool bindings.
- [Migration changes current model behavior] → Characterize names, schemas,
  eligibility, provider ordering, limits, source capture, cancellation, and
  failures before refactoring, then run the same tests through the registry.
- [A tool is incorrectly exposed to widgets] → Default migrated tools to
  `MODEL` only and require explicit `WIDGET` eligibility plus a bounded
  structured result codec.
- [Global enablement surprises users across consumers] → Treat user
  enablement as permission to use the tool, but always display and enforce
  consumer eligibility separately; later per-widget consent belongs to the
  widget lifecycle change.
- [A provider token leaks through generalized metadata] → Keep current
  encrypted stores and secret-supplying closures outside descriptors and
  serialized invocations; test redaction on every boundary.
- [Future persisted plans outlive a tool contract] → Require explicit
  contract versions and reject unsupported versions rather than silently
  upgrading. Migration semantics will be specified with widget persistence.

## Migration Plan

1. Characterize the existing calculator, Wikipedia, and provider-neutral web
   search paths before changing composition.
2. Introduce the registry, typed binding, readiness, dispatch result, and fake
   consumer tests with no production tool migrated.
3. Register existing tools as `MODEL`-only and adapt their current enablement
   and credential stores without changing persisted keys or secrets.
4. Route LiteRT-LM adapter execution through shared dispatch while preserving
   model-visible protocols and retained-conversation compatibility.
5. Add the widget gateway and verify it with fake widget-only and dual-consumer
   registrations; do not connect it to navigation, UI, persistence, or workers.

Rollback restores the previous direct tool composition. No new durable user
data or credential format is created, so rollback requires no data migration.
