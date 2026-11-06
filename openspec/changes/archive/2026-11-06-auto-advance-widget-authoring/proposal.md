# Automatically advance validated widget-authoring stages

## Why

The existing authoring pipeline requires an Advance tap after every successful stage, even though validation and dependency checks are deterministic. This makes a multi-stage widget request needlessly interactive.

## What Changes

- Treat starting or retrying an authoring attempt as authorization to continue through later validated stages in the same process.
- Persist and unload after each stage as before, then accept a validated candidate and submit the next dependency-satisfied stage automatically.
- Stop at the first failure, cancellation, infeasible or clarification outcome, unavailable configuration, or invalid assembly. Do not automatically retry or reprocess.
- Stop at the final draft preview and retain explicit user consent for saving, enabling, permissions, and scheduling.
- Keep the latest bounded, actionable rejected artifact available on every manual repair, without allowing it to exhaust the model input budget.

## Impact

Widget-authoring workflow orchestration, stage notifications, tests, and user guidance. Existing durable attempts and checkpoints remain compatible; interrupted sessions do not resume model generation silently after process restart.
