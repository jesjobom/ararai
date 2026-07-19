# Change: Test critical Compose user journeys

## Why

Domain and Android boundary tests are strong, but the automated suite does not
exercise critical Compose journeys. Navigation, prompt controls, model retry,
session dialogs, generation cancellation, and Settings can regress while the
quality gate remains green.

## What Changes

- Add Compose UI test dependencies and deterministic app/screen harnesses.
- Cover a focused set of high-value journeys using semantics, not pixels.
- Inject fake engine, state, and platform services to avoid network/models/devices.
- Keep the suite small, deterministic, and suitable for the common quality gate.

## Impact

- Affected spec: `local-llm-hub`
- Affected code: Compose semantics/testability, Android test configuration, UI tests
- Production UI behavior: unchanged unless characterization reveals a defect
