## 1. Catalog and presentation policy

- [x] 1.1 Add backward-compatible explicit model-family metadata to catalog
  types, parsing, checked-in entries, and parser tests.
- [x] 1.2 Add tested pure logic for workload filtering, family-preserving
  light-to-heavy ordering, and available-memory recommendation.

## 2. Model Management UI

- [x] 2.1 Add Chat and Transcription tabs and render each ordered workload
  catalog independently.
- [x] 2.2 Sample available device memory, explain the recommendation basis, and
  mark fitting models as recommended without disabling other actions.
- [x] 2.3 Expose one benchmark action on every locally available model card and
  route to the exact reasoning or transcription model.
- [x] 2.4 Share the six-thread production transcription default with the
  transcription benchmark while retaining selectable comparison values.

## 3. Navigation and Home

- [x] 3.1 Remove the standalone reasoning diagnostics card from Home and make
  benchmark back navigation return to Model Management.
- [x] 3.2 Update deterministic Compose journeys for the new Home signature,
  workload tabs, ordering, recommendation, and benchmark actions.

## 4. Validation and documentation

- [x] 4.1 Update consolidated product documentation for workload tabs,
  per-model benchmarks, ordering, and advisory recommendations.
- [x] 4.2 Run targeted tests, the project quality gate, strict OpenSpec
  validation, and `git diff --check`.
- [x] 4.3 Build and copy the debug APK for device installation.
