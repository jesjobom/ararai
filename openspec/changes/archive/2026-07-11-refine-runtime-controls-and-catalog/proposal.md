# Refine Runtime Controls And Catalog

## Why

The app can now run multiple local runtimes, so long-running operations need
explicit cancellation controls and Android back behavior should keep the user
inside the app shell instead of exiting from internal screens. The model catalog
also needs pruning now that Gemma LiteRT-LM is the useful Gemma path.

The displayed app version should be generated from build time to make APK
handoff testing unambiguous without relying on manual version bumps.

## What Changes

- Make Android back return to Home from internal screens.
- Add cancel actions for model downloads, chat generation, and benchmark runs.
- Keep downloads streamed to a `.part` file and clean that file on cancellation.
- Generate the displayed version from a build timestamp.
- Remove Gemma GGUF CPU and Phi-4 from the checked-in model catalog.

## Impact

- Cancelled downloads leave the configured model missing or at its current valid
  state, with temporary files removed.
- Cancelled chat and benchmark work unload runtime resources.
- APK version label changes every build.
