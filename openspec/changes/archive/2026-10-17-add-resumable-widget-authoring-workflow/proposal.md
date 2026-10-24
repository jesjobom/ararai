# Proposal

## Why

Physical-device validation showed that a continuous multi-stage authoring job
can be killed by Android memory pressure while reloading the local model,
discarding all validated work completed earlier in the pipeline. Users also
cannot currently inspect a stage result, decide when to continue, or retry one
stage without restarting the complete authoring request.

## What Changes

- **BREAKING**: Replace automatic end-to-end authoring with a resumable workflow
  that stops after every model-generated artifact and waits for an explicit
  user action before advancing.
- Persist the authoring session, validated checkpoints, bounded captured output,
  validation result, attempt history, and dependency/version metadata so work
  survives process death and app restart.
- Show each feasibility, algorithm, call-function, plan, and render attempt in a
  user-visible timeline with success or controlled failure details.
- Let the user continue from an accepted checkpoint, retry a failed stage, or
  reprocess a successful stage. Accepting a replacement SHALL make every
  dependent downstream checkpoint stale.
- Run one model generation per user-authorized stage attempt, then unload the
  model, persist the result, stop foreground execution, and notify the user that
  review or another action is required.
- Keep rejected artifacts inert and bounded: they may be displayed as escaped
  text and used as controlled repair context, but SHALL NOT be executed,
  activated, or treated as a capability grant.
- Preserve application-owned validation, capability derivation, deterministic
  assembly, preview, consent, and final widget confirmation.
- Replace the whole-pipeline deadline with per-attempt limits and persist repair
  budgets across restarts.
- Remove application-owned protocol and source-fragment identity fields from
  model-facing schemas, derive call metadata and the plan function locally, and
  keep legacy persisted envelopes readable while an active session is resumed.
- Normalize an overlong achievable display name locally, strengthen the
  presentation grammar and failure fixtures, and report recovery timeouts
  separately from model-generation timeouts.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `llm-widget-authoring`: Change background authorship from one process-lifetime
  pipeline job into a durable, user-controlled, checkpointed workflow with
  inspectable attempts, targeted retry, safe reprocessing, and process-death
  recovery.

## Impact

- Depends on the still-active `add-managed-llm-authored-widgets` change. Before
  this change is archived, its delta must be reconciled after that foundational
  change becomes canonical, especially the former in-memory-only intermediate
  artifact and automatic-repair requirements.
- Authoring pipeline orchestration, job state, foreground-service lifetime,
  notification actions, and Compose authoring UI.
- Local managed-widget persistence schema and migration, plus cleanup of
  abandoned or explicitly discarded authoring sessions.
- Pipeline APIs must execute and validate one stage attempt from persisted
  upstream checkpoints instead of owning the complete in-memory sequence.
- Recovery admission now considers model-specific memory headroom in addition
  to the existing device signals.
- Existing model/tool/runtime security boundaries and final confirmation remain
  authoritative; no new provider, network, or JavaScript authority is added.
