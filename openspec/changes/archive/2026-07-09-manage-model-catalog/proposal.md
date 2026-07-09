# Manage Model Catalog

## Why

ArarAI started with one fixed configured GGUF model so the first local inference
slice could stay small. The next product step is to let the user manage model
files directly instead of treating the model as a hidden startup dependency.

## What Changes

- Introduce a configured model catalog that can contain more than one GGUF
  model while preserving the existing single-model configuration format.
- Keep the catalog static and app-defined; users can manage only models already
  declared by configuration.
- Replace the single model status flow with a model list that shows each
  configured model's local state.
- Add per-model actions for download, retry, update/redownload, delete, and
  selecting the active model.
- Keep chat wired to the selected model's current availability state.
- Automatically download the configured default model only when no configured
  model is already available locally.

## Impact

- Affects model configuration parsing, startup model state management, and the
  model status UI.
- Does not add an unverified public model list or remote catalog sync.
- Does not persist arbitrary user-added model definitions yet.
