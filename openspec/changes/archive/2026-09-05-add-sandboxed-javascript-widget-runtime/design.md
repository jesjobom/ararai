## Context

The shared application-tool platform already provides stable versioned
contracts and a model-independent `WidgetToolExecutionGateway`, but the only
caller is a test seam. Its dispatcher intentionally validates structured
invocations and does not interpret generated executable code. ArarAI has no
JavaScript dependency or widget program format today. The Android baseline is
API 28+, Java/Kotlin 17, arm64-v8a, optimized R8 release builds, and existing
NDK/CMake integration.

See `proposal.md` for the motivation. The behavioral boundary is defined in
`specs/widget-script-runtime/spec.md`.

## Goals / Non-Goals

**Goals:**

- Establish a small data-only boundary between untrusted JavaScript and Kotlin.
- Prove isolation, termination, deterministic inputs, package integrity, and
  optimized Android compatibility before a product workflow depends on them.
- Support ordinary program structure without making JSON the behavior language.
- Preserve the existing application-tool registry as the only effectful tool
  execution path.
- Keep the runtime replaceable behind Kotlin contracts if the engine changes.

**Non-Goals:**

- Authoring or repairing programs with an LLM.
- Persisting, scheduling, rendering, listing, or editing widgets.
- Exposing arbitrary JavaScript packages, browser APIs, Java/JNI objects, or a
  general plugin system.
- Adding a production `WIDGET` tool or changing existing model-visible tools.
- Guaranteeing compatibility with arbitrary ECMAScript or Node/browser code.

## Decisions

### Gate QuickJS before accepting the dependency

Build a minimal arm64 Android spike first and record the exact QuickJS source or
bridge version, origin, license, checksums, transitive/native artifacts, ABI
contents, uncompressed APK delta, cold execution cost, and R8 release result.
The spike must demonstrate the runtime memory limit, stack limit, interrupt
handler, cancellation flag, and isolation tests on the supported Android
baseline. If any hard gate fails, do not implement the remaining tasks; compare
Rhino against the same contract and amend this design before proceeding.

QuickJS is preferred because a runtime/context can be isolated without exposing
the JVM and its native API supports memory, stack, and interrupt controls.
MVEL was rejected because its strength is reflective integration with Java
object graphs and it does not provide a comparable security/resource boundary
for generated code. A custom JSON AST was rejected as the authoring language
because control flow would be verbose and the project would own a growing
parser/interpreter. Rhino remains a fallback because it is JVM-native, but its
Java integration would need to be completely disabled and its isolation must be
proved rather than assumed.

### Separate program metadata from source and lifecycle

`WidgetProgram` contains normalized `WidgetProgramManifest` data and UTF-8
source. The manifest owns only executable-package concerns:

- manifest schema and widget API versions;
- JavaScript language/runtime compatibility identifier;
- planning and presentation entrypoint names;
- requested capability identifiers and bounded parameters;
- requested resource profile no higher than a checked-in policy; and
- SHA-256 of the exact source bytes.

The parser rejects unknown fields in a known schema version and canonicalizes
accepted JSON before hashing or comparison. Application-owned IDs, display
name, schedule, enablement, consent, and active revision belong to the later
managed-widget definition, not this manifest. Source remains separate so it can
be reviewed and diffed without JSON escaping and its integrity can be checked
before evaluation.

### Use a data-only JNI/runtime adapter

The Kotlin runtime adapter accepts source plus bounded canonical JSON input and
returns canonical JSON or a stable failure code. It never passes an Android,
Kotlin, Java, registry, gateway, HTTP, credential, or persistence object into
QuickJS. A new QuickJS runtime/context is created for each phase and always
destroyed in `finally`; mutable globals therefore cannot bridge planning and
presentation or survive another run.

The QuickJS global environment removes or leaves unavailable dynamic
evaluation, `Function`, module loading, WebAssembly, direct clock/randomness,
timers, console sinks that reveal host data, and browser/Node APIs. A small
frozen bootstrap exposes pure versioned helpers over copied JSON values. Inputs
are deep-frozen. Any pseudo-random helper consumes an explicit host seed.

Keeping the bridge data-only was chosen over host callbacks or a JavaScript
`ctx.tools.call()` implementation because synchronous/async callbacks enlarge
the JNI attack surface, complicate cancellation, and let code interleave
effects with arbitrary control flow.

### Execute a two-phase protocol

The program has two independent entrypoints:

1. `plan(context)` returns an array of uniquely aliased `{toolId,
   contractVersion, arguments}` requests.
2. Kotlin validates the entire plan, executes permitted requests through
   `WidgetToolExecutionGateway`, and collects canonical success/failure
   envelopes. It does not execute a partially invalid plan.
3. `render(context, outcomes, state)` runs in a fresh isolate and returns one
   constrained presentation tree.

The first API version has one plan round; tool results cannot create another
round of calls. This makes call count and network behavior knowable before any
provider is contacted. A future version can add a bounded state-machine
protocol without silently changing v1.

The application computes the effective grant as the intersection of manifest
requests, caller consent, registered tool descriptors, and fixed policy.
Arguments still pass through the registry's contract decoder and the gateway
still enforces `WIDGET` eligibility and readiness. The script-runtime
coordinator interprets code; the dispatcher does not, so the existing shared
execution requirement remains intact.

### Validate presentation as a versioned intermediate model

The initial output grammar is a bounded tree using application-defined nodes
such as card, column, row, text, icon, value, and link action, with semantic
style tokens instead of arbitrary colors, dimensions, HTML, Compose, or
callbacks. The foundation validates and normalizes this tree but does not render
it. Link actions accept only an allowlisted HTTPS URL copied from an identified
tool result field; later UI code owns intent creation.

Node count, depth, text length, collection size, and serialized bytes are part
of the checked-in API policy. Unknown nodes or properties are rejected rather
than ignored so a program cannot depend accidentally on runtime-specific
behavior.

### Enforce limits at every boundary

Preflight validation bounds manifest/source/input bytes and declared
capabilities. QuickJS receives a runtime memory ceiling, maximum stack, and an
interrupt handler backed by both a monotonic deadline and an atomic
cancellation flag. JSON conversion bounds depth, entries, strings, numeric
finiteness, and output bytes. The outer coroutine deadline destroys the isolate
and discards late JNI output even if an engine defect delays cooperative
interruption.

Stable failures distinguish invalid package, incompatible version, denied
capability, invalid plan/output, script error, resource limit, cancellation,
tool rejection, and runtime unavailability. JavaScript messages, stack traces,
source fragments, native addresses, and arbitrary exception text do not cross
the production result boundary.

## Risks / Trade-offs

- [A native engine vulnerability executes inside the app process] → Pin and
  verify the engine, expose no host objects, keep input/output bounded, document
  the residual in-process risk, and track upstream security updates.
- [Wall-clock interruption is not an exact instruction budget] → Combine the
  QuickJS interrupt callback with strict small inputs/outputs and an outer
  deadline; record measured worst-case termination in the feasibility gate.
- [JavaScript numeric semantics surprise future financial widgets] → Keep the
  initial API explicit about finite JSON numbers and add a versioned
  application-owned decimal-string helper before a financial tool is shipped.
- [Recreating an isolate adds latency] → Prefer reproducibility and containment;
  measure cold cost and optimize the bootstrap only if the bounded fixture
  budget is missed.
- [Removing globals differs across engine upgrades] → Treat the available
  global surface as a tested compatibility contract and rerun adversarial tests
  on every dependency update.
- [The manifest asks for excessive limits] → Treat manifest values only as
  requests and reject values above the fixed application profile.

## Migration Plan

1. Complete the QuickJS feasibility gate without production composition.
2. Add pure package, grant, result, and presentation contracts with failing unit
   tests before the native adapter.
3. Integrate the runtime behind those contracts and pass deterministic and
   adversarial host tests plus Android instrumentation tests.
4. Connect only fake `WIDGET` tools through the existing gateway and verify no
   model, UI, persistence, or worker is constructed.
5. Document the final dependency and resource profile, then run the full quality
   gate and optimized release-candidate checks.

Rollback removes the new runtime composition and dependency. No durable data,
preference, worker, navigation destination, or production tool contract is
created, so rollback requires no data migration.
