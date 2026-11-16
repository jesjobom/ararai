# Shared conversation memory

## Why

Chat sessions currently share durable conversation storage but not durable context. Users need to explicitly save small facts or preferences that remain available in later Chat and Voice Chat sessions, with a visible way to edit and remove them.

## What changes

- Rename the assistant configuration's Prompts tab to Context and show mode-specific prompts and shared memory together.
- Persist ordered, individually editable memory entries on device. The active mode's prompt and shared memory consume one bounded editable-context allowance; prompts are never rotated, while the oldest memory entries are evicted whole when needed.
- Add a model-only local memory-write tool for supported Chat models, with a Tools-tab switch enabled by default. The tool is never exposed to widgets. Instruct the model to use it only when the user asks it to remember.
- Enable the local calculator by default while preserving any explicit user opt-out.
- Include shared memory in every new Chat and Voice Chat turn across sessions, and invalidate a retained native conversation when that effective context changes.

## Scope

No cloud sync, automatic extraction of facts, semantic retrieval, or widget access. Existing per-mode prompts and conversation histories remain intact.
