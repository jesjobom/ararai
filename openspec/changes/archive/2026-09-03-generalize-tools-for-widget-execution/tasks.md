## 1. Characterize current tool behavior

- [x] 1.1 Add regression tests that capture the current Wikipedia, web-search,
  and calculator model-visible names, schemas, enablement gates, and selected-
  model capability rules; verify the focused tests pass before refactoring.
- [x] 1.2 Characterize current provider priority, fallback, credential readiness,
  per-turn call ceilings, lifecycle events, source capture, cancellation, and
  controlled failures; verify each behavior with deterministic local fakes.

## 2. Establish the shared tool platform

- [x] 2.1 Introduce stable tool identity/version, category, consumer eligibility,
  bounded contract metadata, readiness, invocation context, and controlled
  dispatch result types; verify construction tests cover all valid metadata
  combinations.
- [x] 2.2 Implement the immutable application tool registry and verify tests
  reject duplicate ID/version pairs, empty consumer sets, malformed contracts,
  unknown tools, and unsupported versions deterministically.
- [x] 2.3 Implement typed request/result bindings with bounded serialized codecs
  and verify malformed, additional, oversized, and incorrectly typed arguments
  are rejected before a fake executor is called.
- [x] 2.4 Implement suspending shared dispatch with enablement, readiness,
  consumer, policy, cancellation, and single-invocation enforcement; verify
  model-only, widget-only, dual-consumer, disabled, unconfigured, cancelled,
  timed-out, and successful fake-tool paths.

## 3. Adapt tool configuration and secrets

- [x] 3.1 Add narrow configuration/readiness adapters over current Wikipedia,
  calculator, and web-provider preference stores; verify existing persisted
  enablement and configured/unreadable credential states resolve unchanged.
- [x] 3.2 Resolve the provider-neutral `web_search` readiness from the current
  verified enabled-provider chain without moving credential values into generic
  tool state; verify Exa-first/Tavily-fallback behavior remains unchanged.
- [x] 3.3 Add boundary tests proving credentials and encrypted representations
  never appear in registry metadata, invocation arguments, results, logs, or
  controlled diagnostics.

## 4. Register and migrate existing tools

- [x] 4.1 Register Wikipedia, provider-neutral web search, and calculator with
  stable IDs, contract version 1, current categories, typed bindings, and
  explicit `MODEL`-only eligibility; verify registry discovery returns exactly
  those intended non-secret contracts.
- [x] 4.2 Route the existing domain implementations through shared dispatch
  while preserving their HTTP/local-compute bounds and domain results; verify
  the pre-refactor provider and calculator regression suites pass.
- [x] 4.3 Compose the registry once at the application/controller composition
  boundary and inject narrow consumers rather than exposing it to UI code;
  verify application recreation tests do not duplicate registrations or change
  stored configuration.

## 5. Preserve model tool calling

- [x] 5.1 Refactor the LiteRT-LM OpenAPI adapters to resolve and execute
  `MODEL`-eligible registry bindings while retaining their current synchronous
  SDK bridge, schema, call counters, lifecycle events, result framing, and
  source capture; verify the characterization tests remain green.
- [x] 5.2 Resolve advertised model tools as the intersection of registry entry,
  user enablement, operational readiness, `MODEL` eligibility, and selected-
  model capability; verify disabled, unconfigured, unsupported-model, and
  widget-only entries are never advertised.
- [x] 5.3 Preserve retained-native-conversation invalidation from the resolved
  model tool-name set; verify enabling, disabling, configuring, or removing a
  model-visible tool invalidates only incompatible conversation state.
- [x] 5.4 Run focused Chat and Voice Chat controller tests and verify successful,
  failed, and cancelled tool turns still recover without protocol leakage or
  changes to canonical conversation persistence.

## 6. Add the widget execution seam

- [x] 6.1 Add a suspending widget-domain gateway over shared dispatch and verify
  a fake `WIDGET`-eligible tool executes without constructing a model engine or
  a LiteRT-LM OpenAPI adapter.
- [x] 6.2 Verify the widget gateway returns controlled failures for model-only,
  disabled, unconfigured, malformed, unknown-version, cancelled, timed-out, and
  unavailable invocations without calling an ineligible executor.
- [x] 6.3 Verify the widget gateway performs no widget creation, rendering,
  persistence, navigation, scheduling, worker registration, automatic retry, or
  cache mutation.

## 7. Documentation and validation

- [x] 7.1 Update architecture documentation to describe the registry, consumer
  eligibility, configuration/readiness split, model adapter, and widget gateway,
  while marking all widget lifecycle features and new data providers as
  deferred; verify documentation agrees with the final contracts.
- [x] 7.2 Run focused registry, dispatch, configuration, provider, calculator,
  engine, Chat, Voice Chat, and widget-gateway tests; verify all pass with no
  live network dependency.
- [x] 7.3 Run `scripts/quality-gate.sh`, `openspec validate --all --strict`, and
  `git diff --check`; verify each command completes successfully and record
  physical-device model tool-calling checks as not executed unless actually
  performed.

Physical-device model tool-calling checks were not executed for this change;
the existing model-visible contracts are covered by deterministic adapter,
engine, Chat, and Voice Chat tests.
