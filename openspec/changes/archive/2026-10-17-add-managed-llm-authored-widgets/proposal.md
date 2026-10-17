## Why

The sandboxed widget program runtime alone is not a user-facing feature.
ArarAI needs a complete local lifecycle in which a user can describe a widget,
review the LLM-authored program and permissions, confirm it, and then manage its
scheduled executions and results without loading a model for each refresh.

## What Changes

- Add a dedicated in-app Widgets destination for listing, creating, inspecting,
  enabling, disabling, refreshing, editing, duplicating, and deleting managed
  widgets. This does not add Android launcher/app widgets.
- Add local versioned persistence for managed definitions, immutable program
  revisions, cached presentations, observations, and sanitized execution logs.
- Add a dedicated bounded multi-round local-model authorship pipeline. It first
  determines feasibility and required registered tools, then proposes a typed
  high-level algorithm, generates and validates small JavaScript fragments,
  and assembles one complete dynamic program without letting a later round
  rewrite already accepted fragments. Normal Chat and Voice Chat do not receive
  any authoring-stage tool.
- Expose the current authoring stage in the foreground UI, support cancellation
  throughout the pipeline, and allow at most a small checked-in number of
  targeted repairs for the failing stage. Persist nothing before confirmation.
- Validate every proposal before showing a preview, permission/schedule
  summary, and edit diff; persist or activate nothing until the user confirms.
- Re-author edits from the current normalized definition, preserve
  application-owned identity/ownership, and require renewed confirmation when
  capabilities, tools, data retention, or execution frequency expand.
- Schedule unique, non-overlapping background work with WorkManager, execute
  without model inference, retain the last valid presentation on recoverable
  failure, and expose bounded local run history and stale/error state.
- Add structured `wikipedia_pages` and `wikipedia_on_this_day` tools for both
  model and widget consumers. The former returns bounded page-search results;
  the latter returns bounded historical events for an explicit calendar date.
- Give models explicit selection guidance: use `wikipedia_on_this_day` for
  events on a month/day and `wikipedia_pages` for direct stable encyclopedic
  page lookup. Keep transport, URLs, limits, and parsing application-owned.
- Deliver one complete Wikipedia vertical slice in which a prompt derives the
  current local month/day and selects one actual event for display.
- Defer arbitrary network endpoints, downloaded plugins, Android launcher
  widgets, financial providers, cross-widget data access, remote sync, and
  unattended LLM inference during scheduled refreshes.

## Capabilities

### New Capabilities

- `managed-widgets`: Defines the local widget lifecycle, revisioned
  persistence, scheduling, execution state, observations, logs, constrained
  rendering, and management UI.
- `llm-widget-authoring`: Defines prompt-based creation and editing, structured
  program proposals, validation, preview/diff, consent, and model/session
  isolation.

### Modified Capabilities

- `application-tool-platform`: Add dual-consumer structured Wikipedia page and
  on-this-day contracts while preserving the legacy model-facing binding for
  compatibility and the shared dispatch boundary.
- `local-llm-hub`: Allow eligible local models to receive the temporary
  stage-specific capture tools only inside a dedicated widget-authoring
  pipeline gated by the versioned `widget_authoring_pipeline_v1` catalog
  capability.
- `widget-script-runtime`: Add a deterministic runtime standard library backed
  by a fresh application-supplied execution snapshot, including current local
  date/time access without ambient wall-clock or host-object exposure.

## Impact

- Dependency: source implementation may begin after the automated foundation
  gates pass, its runtime contracts are captured, and an optimized manual
  physical-validation APK is available. The foundation and this change MUST
  NOT be archived, accepted for release, or expose production widget activation
  until the pending physical arm64 report passes.
- Affected code: Home navigation, widget screens/controllers, multi-stage
  authoring coordinator and adapters, deterministic runtime standard library,
  local persistence, WorkManager coordination, program execution and
  rendering, Wikipedia tool binding, configuration/composition, and tests.
- Persistence/privacy: widget source, definitions, cached structured results,
  observations, and sanitized logs remain app-private and local; provider
  credentials and raw tool transports never enter them, and Android backup
  remains disabled.
- Network: confirmed executions may contact only application-owned providers
  of declared enabled `WIDGET` tools; the first production destination is the
  existing Wikipedia host family.
- Compatibility: the legacy `wikipedia_search@1` binding remains registered,
  while the checked-in models advertise the clearer structured page/event
  pair. Wikipedia enablement remains one explicit user preference shared by
  both tools and model-independent scheduled execution remains unchanged unless
  a user explicitly creates and enables a widget. The unsuccessful one-shot
  authoring protocol has no confirmed durable widgets to migrate; existing
  valid stored program packages remain executable and editable through the
  revised pipeline.
