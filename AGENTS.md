# AGENTS.md - ArarAI repository guide

## Source of truth

Read `openspec/project.md` before changing behavior. Follow its precedence:

1. Consolidated requirements under `openspec/specs/`.
2. An approved active change under `openspec/changes/<name>/`.
3. Source, resources, manifests, Gradle configuration, and tests.
4. `README.md` and supporting documents.

Archived changes are history, not active implementation plans.

## Change workflow

- Use OpenSpec for behavior, architecture, workflow, privacy, or validation changes.
- Keep changes small, compatible, and aligned with the existing package boundaries.
- Prefer deterministic tests and local fakes; do not introduce remote services or a general DI framework only for testing.
- Preserve local-first behavior and app ownership of conversations, media, models, preferences, and runtime caches.
- Do not edit generated output under `build/`, `.gradle/`, or `.cxx/`.

## Required validation

Run the common local and CI gate before declaring a change complete:

```sh
scripts/quality-gate.sh
```

Use targeted tests while iterating. Do not add new findings to the Detekt baseline
as a routine fix. See `docs/quality-gates.md` for exact coverage and exclusions.

## Device boundary

A green automated gate does not prove real LiteRT-LM or whisper.cpp inference,
GPU/backend behavior, microphone capture, TTS behavior, Android lifecycle under
load, memory pressure, or thermal behavior. Follow `docs/device-validation.md`
and report device/model/build evidence explicitly; otherwise mark those checks as
not executed.

## Delivery

Do not commit, push, archive an OpenSpec change, or copy an APK unless the task
explicitly includes that delivery step. Preserve unrelated working-tree changes.
