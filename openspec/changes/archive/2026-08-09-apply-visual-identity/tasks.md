# Tasks

## Proposal

- [x] Define the visual-identity scope and reviewed source-artwork baseline.
- [x] Record the current monochrome and notification-mask limitation.

## Asset Preparation

- [x] Create optimized repository-owned derivatives from the reviewed source
      artwork without stretching or changing its aspect ratio.
- [x] Define the brand background color `#010923` as an Android resource.
- [x] Keep source attribution and asset-generation guidance in repository
      documentation so future replacements are repeatable.

## Launcher And Splash

- [x] Add adaptive foreground/background launcher resources and reference them
      from the application manifest.
- [x] Add compatible legacy launcher resources for the supported Android
      baseline.
- [x] Add Android 12+ and pre-Android-12 splash themes using `#010923` and the
      compact color symbol.
- [x] Hand off from the launch theme to the existing Compose application theme
      without an intermediate blank or light frame.

## Home And README

- [x] Add the transparent wordmark to the top of Home on a fixed `#010923`
      brand surface while preserving accessible app-name semantics and the
      current version label.
- [x] Keep the Home header responsive without cropping or distorting the
      wordmark on supported portrait widths and font/display scales.
- [x] Add an optimized opaque banner near the top of `README.md` with useful
      alternative text and a repository-relative path.

## Tests First

- [x] Add failing resource/configuration tests for application launcher icon
      and splash-theme wiring where practical.
- [x] Add or update Home UI tests for the branded header's semantics and
      retained version information.

## Validation

- [x] Run targeted unit/Robolectric tests while iterating.
- [x] Run `scripts/quality-gate.sh`.
- [x] Run `openspec validate apply-visual-identity --strict`.
- [ ] Manually inspect launcher rendering with representative circle, squircle,
      rounded-square, and themed-icon configurations; record themed-icon work
      as deferred if no valid monochrome mask is available.
- [ ] Manually inspect cold launch on Android 11 or earlier and Android 12+ for
      background consistency, icon clipping, and transition flashes.
- [ ] Manually inspect Home in light/dark themes and representative compact,
      large-font, and large-display-scale configurations.
- [ ] Verify the README banner in GitHub light and dark themes.
