# Design: Bounded local mathematical evaluation

## Context

ArarAI currently exposes structured Wikipedia and web-search tools through types
named around `KnowledgeTool`. Those implementations retrieve untrusted external
content and attach source provenance. A calculator instead consumes a bounded
expression, performs deterministic local computation, and returns application-
owned data. Reusing the LiteRT-LM structured-call loop is appropriate; pretending
that local computation is a knowledge provider is not.

The engine choice is unresolved. Building an expression parser in the application
would create avoidable correctness and security risk, but accepting a third-party
engine without evaluating its grammar, numerical model, license, Android behavior,
maintenance, transitive graph, and size would move that risk into the dependency.

## Decisions

### Make library research an implementation gate

Before production code or dependency changes, compare viable maintained engines
and the option of a deliberately smaller app-owned grammar. The evaluation SHALL
cover at least:

- license and redistribution compatibility;
- Android API 28 and Kotlin/JVM compatibility;
- recent maintenance, published artifact integrity, and supply-chain posture;
- transitive dependencies and measured debug/release binary-size impact;
- grammar, supported operators/functions/constants, precedence, and locale rules;
- numerical representation, precision, rounding, overflow, non-finite values,
  domain errors, and reproducibility;
- limits or interruption controls for pathological expressions;
- thread safety, startup/evaluation cost, and testability;
- resistance to dynamic code execution, reflection, filesystem/network access,
  and unexpectedly extensible functions.

The evaluation artifact SHALL identify the selected approach and rejected
alternatives with evidence. If the findings change supported operations,
precision guarantees, limits, dependency policy, UI disclosure, or feasibility,
this active change SHALL be revised and strictly validated before implementation
continues. A library is preferred only when it clears the gate; the proposal does
not predetermine the winner.

### Generalize the tool execution boundary

Introduce a runtime-neutral application-tool abstraction that can describe a
structured schema, execution locality, display state, bounded result, and
controlled failure. Existing knowledge tools remain a specialized external-data
category with their current provenance and trust handling. The calculator is a
local-compute category and does not manufacture citations or reuse web-specific
request/result models.

This refactor SHALL preserve the existing Wikipedia and web-search contracts and
their per-turn limits. It SHALL not introduce a general plugin system or dynamic
tool loading.

### Keep the model-facing calculator contract narrow

The initial structured call exposes one expression and no executable callback,
script, arbitrary precision setting, or user-controlled function definition. The
final grammar and supported function set follow the research gate, but the
baseline must support finite decimal arithmetic with grouping and the four basic
operators. Additional powers, roots, constants, or transcendental functions are
included only when their semantics and limits are documented by the selected
approach.

Inputs are bounded by schema and revalidated before evaluation. Results use a
locale-independent machine representation for the model and may carry a separate
human-readable representation. Failures use stable categories such as invalid
expression, unsupported operation, domain error, non-finite result, complexity
limit, timeout/cancellation, and internal unavailability. The app never guesses a
numeric answer after evaluator failure.

### Preserve capability gating and local privacy

Calculator enablement is locally persisted and disabled by default. The tool is
registered only when enabled and when the selected installed model explicitly
declares verified structured calculator capability. Ineligible models continue
normal local generation without a textual command protocol or parsing ordinary
assistant text as tool calls.

The calculation engine performs no network request and receives no conversation
history beyond the validated expression arguments. Calculation intermediates are
ephemeral. Completed assistant text remains normal canonical history; raw tool
protocol and intermediate values are not persisted as visible messages.

### Defer plotting and symbolic algebra

Plotting needs a separate structured plot specification, sampling/discontinuity
policy, renderer, accessibility behavior, and persistence/export decisions.
Symbolic manipulation carries a substantially larger grammar and correctness
surface. Neither is implied by selecting an engine that happens to support it;
both require later OpenSpec changes.

## Validation

Automated tests SHALL cover the selected grammar and numerical policy with known
vectors, malformed and adversarial expressions, limits, locale independence,
cancellation, registration gating, tool-loop completion, and regression coverage
for existing knowledge tools. Dependency locking and verification metadata SHALL
be updated for any selected artifact. The full quality gate and strict OpenSpec
validation are required.

Physical-device checks SHALL record real LiteRT-LM tool selection and final answer
synthesis, responsiveness, memory, and binary/install impact. Automated evaluator
tests do not prove that a model will reliably choose the calculator for every
appropriate prompt.
