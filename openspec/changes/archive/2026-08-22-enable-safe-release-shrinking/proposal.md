## Why

Release builds explicitly disable R8 even though optimized ProGuard defaults and
project rules are present. This increases shipped bytecode and omits mapping
metadata, but enabling shrinking without evidence could break reflection, JNI,
Firebase, Compose, or LiteRT-LM paths.

## What Changes

- Establish a reproducible release assembly and focused smoke baseline before
  enabling shrinking.
- Review project and dependency keep rules, enable R8 for release, and retain
  mapping/usage artifacts needed for diagnostics.
- Validate startup and critical local/Firebase/JNI flows on a release-like
  physical-device build; size reduction alone is not acceptance evidence.

## Capabilities

### Modified Capabilities

- `build-delivery`: Release artifacts use validated code shrinking and preserve diagnostic mapping artifacts.

## Impact

- Affected files: release build configuration, ProGuard/R8 rules, release checks,
  delivery documentation, and device-validation evidence.
- Risk: incorrect keep rules can cause release-only runtime failures.
