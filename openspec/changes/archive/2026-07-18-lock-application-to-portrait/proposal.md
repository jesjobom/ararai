# Change: Lock the application to portrait orientation

## Why

ArarAI's current phone UI is designed and validated for vertical use. Landscape
rotation reduces useful reading space and introduces an unsupported layout mode
without providing a meaningful workflow benefit at this stage.

## What Changes

- Lock the launcher activity to portrait orientation.
- Prevent device rotation from switching the application into landscape.

## Impact

- Affected spec: `local-llm-hub`
- Affected code: Android application manifest and configuration test
- Application data, inference, privacy, and networking: unchanged
