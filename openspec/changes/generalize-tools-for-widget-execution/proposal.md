## Why

ArarAI's tools are currently discovered and executed primarily through the
model-facing LiteRT-LM path, even though their application-owned executors can
serve other controlled consumers. The planned widget runtime needs to reuse
those executors without loading a model, duplicating provider logic, or making
model compatibility a prerequisite for widget data retrieval.

## What Changes

- Introduce one application-owned tool registry with stable tool identity,
  versioned input/output contracts, category, operational requirements, and
  independently declared `MODEL` and `WIDGET` eligibility.
- Resolve registration, user enablement, credential readiness, consumer
  eligibility, and model capability as separate states instead of treating
  "available to the current model" as the tool's global availability.
- Keep user-controlled enablement and credential configuration shared at the
  tool/provider boundary, while keeping credentials out of model arguments,
  widget invocations, persisted plans, results, and diagnostics.
- Route model tool calling through the shared registry and execution boundary
  while preserving the existing Wikipedia, web-search, calculator, Chat, Voice
  Chat, provenance, failure, and per-turn limit behavior.
- Add a widget-facing tool execution gateway that accepts a bounded validated
  invocation, enforces `WIDGET` eligibility and operational readiness, and
  invokes the same registered executor without model inference.
- Make widget eligibility explicitly opt-in. Existing tools retain their
  current model behavior and are not made widget-compatible merely by being
  migrated to the registry.
- Cover model-only, widget-only, dual-consumer, disabled, unconfigured,
  unknown-version, malformed-argument, cancellation, and controlled-failure
  paths with deterministic tests.
- Defer widget generation, visual schemas, rendering, persistence, management,
  scheduling, background workers, and new quote/weather tools to later changes.

## Capabilities

### New Capabilities

- `application-tool-platform`: Defines shared tool registration, configuration
  readiness, independent consumer eligibility, validated execution, and the
  widget-facing execution gateway.

### Modified Capabilities

- `local-llm-hub`: Resolve model-visible tools through the shared application
  registry without changing existing Chat or Voice Chat tool behavior.

## Impact

- Affected code: application-tool contracts, registry/composition root,
  configuration/readiness adapters, LiteRT-LM OpenAPI tool adapters, current
  knowledge and calculator tools, and a new widget-domain execution gateway.
- Persistence and credentials: existing local enablement and encrypted provider
  credential stores remain authoritative; adapters may expose their state
  through the registry without moving secret material into generic metadata.
- Compatibility: current model-facing tool names, schemas, provider priority,
  call ceilings, source handling, and unsupported-model behavior remain stable.
- Dependencies and network destinations: no new dependency, provider, endpoint,
  permission, or background execution is introduced by this change.
