# Upgrade LiteRT-LM to 0.20.0

## Why
Update the Android runtime used for on-device Gemma 4 inference and verify
compatibility with the existing E2B and E4B bundles on a physical device.

## What Changes
- Upgrade the LiteRT-LM Android dependency and refresh Gradle locks and checksums.
- Record pre- and post-upgrade GPU diagnostics and staged widget authoring.

## Impact
- Affected code: Android build and dependency metadata.
- Affected specs: none. Model files, backend policy, and widget contracts are unchanged.
