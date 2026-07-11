# Change: Material UI Foundation

## Why

ArarAI is moving from a debug-oriented local LLM slice toward a daily-use Android
application. The current Compose screens are functional, but they do not yet
provide a consistent Material app structure for navigation, hierarchy, or
states. A small Material 3 foundation will make the app easier to evolve without
turning diagnostics such as benchmark runs into core product surfaces.

## What Changes

- Establish a Material 3 app foundation with a richer app theme, consistent top
  bars, screen containers, and action hierarchy.
- Rework Home so Chat is the primary daily-use entry point, model management is
  visible, and benchmark remains a secondary diagnostics entry.
- Refresh Chat, Models, and Benchmark screens with consistent Material controls,
  progress/error surfaces, and navigation behavior.
- Keep benchmark scope limited to an on-demand diagnostic tool; do not add
  benchmark history or model comparisons.

## Impact

- Touches Compose UI and theme files only.
- No model runtime, download, or inference behavior changes are intended.
- Existing ViewModel and controller tests remain the main guardrail; build
  validation is required because this is primarily UI composition work.
