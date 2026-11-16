## 1. Contract

- [x] 1.1 Define the shared-memory and prompt-budget behavior in an OpenSpec delta.

## 2. Implementation

- [x] 2.1 Add ordered local memory storage, bounded FIFO writes, and prompt-budget enforcement.
- [x] 2.2 Compose shared memory into Chat and Voice Chat turns and invalidate stale native context.
- [x] 2.3 Register and expose a model-only explicit memory-write tool for verified models.
- [x] 2.4 Replace the Prompts tab with a Context tab offering view, add, edit, and delete controls.
- [x] 2.5 Add a default-on model memory-write switch on Tools, gating advertisement and dispatch without removing saved context.
- [x] 2.6 Enable calculator by default while retaining persisted user opt-outs.

## 3. Validation and documentation

- [x] 3.1 Add focused tests for storage, eviction, prompt composition, and tool dispatch.
- [x] 3.2 Update user/project documentation and run the repository quality gate.
- [x] 3.3 Record the physical-device inference boundary separately from automated checks.
- [x] 3.4 Validate the new switches and defaults on the host and on the retained device.

Automated validation passed on the development host. On 2026-11-14 EST, the
retained SM-S901E passed a non-destructive E4B `memory_write` turn, a persisted
UI-entry check, and cross-session recall after app process restart. See
`evidence/2026-11-14-sm-s901e-memory.md`. Voice Chat and on-device capacity
rotation were not exercised; retain this change unarchived until review.
The default-on memory-write and calculator switches, their reversible opt-outs,
and the Context-tab guidance were checked on the retained SM-S901E after the
quality gate. See `evidence/2026-11-14-sm-s901e-tool-switches.md`.
