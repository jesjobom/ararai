# Change: Align Project Documentation with Current ArarAI

## Why

The README and project context still describe the original text-only,
llama.cpp-only MVP and reference a change path that has been archived. The app
now includes a model catalog, LiteRT-LM, persistent sessions, multimodal input,
reasoning controls, benchmarks, and richer Chat behavior. Stale documentation
causes new work to begin from obsolete product and architecture assumptions.

## What Changes

- Update the README to describe current product capabilities, runtimes, setup,
  verification commands, and physical-device workflow.
- Update `openspec/project.md` to distinguish historical MVP decisions from the
  current product and architecture direction.
- Replace stale planning paths with the canonical spec and active-change workflow.
- Document the source-of-truth hierarchy and a lightweight documentation review
  step for future archived changes.
- Keep user-facing claims bounded to implemented and verified behavior.

## Impact

- Documentation-only change.
- Should be completed after, or explicitly account for, any other improvement
  implemented concurrently so it does not become stale immediately.
- Does not modify product behavior or consolidated requirements by itself.
