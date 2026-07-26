## Context

Model Management currently renders the catalog in declaration order. LLM and
Whisper entries share one scrollable list, reasoning benchmark is opened from
Home for the selected Chat model, and transcription benchmark is opened from an
installed Whisper card. Catalog entries already declare task, expected artifact
bytes, and recommended free RAM, but family identity is implicit in names.

## Goals / Non-Goals

**Goals:**

- Give Chat and Transcription independent, predictable catalog views.
- Keep related variants adjacent while ordering lighter choices first.
- Make an installed model card the single benchmark entry point for both
  workloads.
- Explain recommendations from current available memory and catalog metadata.
- Keep grouping, ordering, and recommendation decisions testable outside
  Compose.

**Non-Goals:**

- Benchmark history, cross-model comparison, or automatic benchmark execution.
- Background monitoring of memory while the screen remains open.
- Preventing downloads or execution when a model is not recommended.
- Adding new model artifacts or inferring support from names.

## Decisions

### Chat is the LLM catalog workload

The Chat tab contains configured Chat/LLM models, including entries that do
not expose optional reasoning controls. This preserves access to the default and
small general-purpose models; interpreting the tab as only
`ModelTask.Reasoning` would hide valid Chat models.

### Family identity is explicit catalog metadata

`ModelConfig` gains a required-in-practice, backward-compatible `family` value
that defaults to the model ID for legacy/single-entry configurations. The
checked-in catalog declares stable families such as `gemma-4` and `whisper`.
Name parsing was rejected because display-name changes would silently reorder
the product.

Within a tab, entries are grouped by family. Families are ordered by their
lightest member's expected artifact bytes, then family ID; variants inside each
family are ordered by expected bytes, then name and ID. Unknown sizes sort last.

### Available memory is sampled at the application boundary

The application reads `ActivityManager.MemoryInfo.availMem` when Model
Management is composed and passes the byte value to pure presentation logic.
A model is recommended when its declared `recommendedFreeRamBytes` is less than
or equal to available memory. Unknown requirements do not produce a
recommendation. This is guidance only because Android available memory is
volatile and runtime overhead varies.

### Benchmark target is explicit and navigation returns to Models

Each available card exposes `Run benchmark`. Chat cards configure the
existing `BenchmarkViewModel` with that exact item before opening its diagnostic
screen. Transcription cards continue to open the exact-model Whisper benchmark.
Back navigation returns to Model Management and the Home diagnostics card is
removed.

The transcription benchmark retains its selectable thread counts but initializes
the selector with the shared six-thread default used by production
transcription. A shared constant prevents the benchmark and Chat/Voice
transcription defaults from drifting.

## Risks / Trade-offs

- [Available memory can change after sampling] → Label the value as currently
  available and treat recommendation as advisory, never as an execution gate.
- [Artifact size is only a proxy for model weight] → Use the checked-in exact
  byte metadata as the stable ordering key and retain explicit RAM requirements
  for suitability.
- [A model can belong to multiple tasks] → Assign transcription entries to
  Transcription first; all other Chat/LLM entries appear in Chat once.
- [Legacy catalogs omit family] → Default family to model ID so parsing and
  behavior remain compatible.

## Migration Plan

Add backward-compatible catalog parsing and checked-in family declarations,
then introduce the pure presentation policy and Compose/navigation changes.
Rollback removes the optional field and restores declaration-order rendering;
downloaded artifacts and persisted model selection require no migration.

## Open Questions

None for this increment.
