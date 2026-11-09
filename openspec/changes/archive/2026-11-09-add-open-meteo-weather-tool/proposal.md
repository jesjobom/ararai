# Change: Add Open-Meteo weather tool

## Why

Current weather questions need fresh data. ArarAI's local model cannot infer
current conditions or reliable forecasts from its weights. The same bounded
weather capability should serve Chat and managed widgets without exposing an
arbitrary HTTP client to model-authored widget programs.

## What Changes

- Add an opt-in, versioned weather tool for current conditions and forecasts
  through Open-Meteo, independently enabled for Chat and widgets.
- Require explicit place names and resolve them with Open-Meteo geocoding.
  If no place is specified, ask the user to name one; never access device location.
- Keep provider requests, variable allowlists, output size, timezone handling,
  place resolution, and attribution in application-owned code.
- Disclose the place-name transfer on both tool activation surfaces and show
  Open-Meteo attribution with weather widget output.

## Impact

- Affected specs: `application-tool-platform`, `managed-widgets`
- Affected code: tool registry and LiteRT bridge, Assistant and Widget tool
  settings, widget presentation, and tests
- Privacy: only an explicitly named place is sent to Open-Meteo after the
  corresponding tool is enabled and invoked; no Android location access is used
- Runtime: location-less requests return a controlled request for a target place
