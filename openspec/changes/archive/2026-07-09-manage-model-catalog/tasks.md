## 1. Model Catalog

- [x] Add a catalog type that supports multiple configured models.
- [x] Preserve compatibility with the existing single-model properties file.
- [x] Populate the initial static catalog with SmolLM2, Llama 3.2 3B, Gemma 4
      E4B, and Phi-4 Mini.
- [x] Add parser tests for multi-model catalog behavior.

## 2. Model Management State

- [x] Add a catalog controller that resolves every configured model.
- [x] Automatically download the configured default only when no configured
      model is available locally.
- [x] Add actions to download, retry, redownload, delete, and select models.
- [x] Add unit tests for model management actions.

## 3. UI Integration

- [x] Replace the single model status screen with a model list.
- [x] Show each model's status, progress, and available actions.
- [x] Feed the selected model state into the existing chat view model.

## 4. Validation

- [x] Run `./gradlew testDebugUnitTest`.
- [x] Run `./gradlew assembleDebug`.
- [x] Copy the debug APK to the OpenClaw artifact handoff path.
