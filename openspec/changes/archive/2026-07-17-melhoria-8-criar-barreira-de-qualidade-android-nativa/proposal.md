# Change: Establish Android and Native Quality Gates

## Why

ArarAI has useful JVM unit coverage, but its defining risks live at Android and
native boundaries: JNI loading, permissions, content providers, lifecycle,
backup rules, and physical-device inference. Those paths currently depend on a
manual checklist and are not protected by a versioned CI quality gate.

## What Changes

- Add repeatable CI checks for unit tests, Android lint, OpenSpec validation,
  and debug assembly.
- Add a small instrumentation suite for high-risk Android boundary behavior.
- Define focused JNI/native smoke coverage that can run without downloading a
  production-sized model where practical.
- Version a physical-device validation matrix for inference, GPU, permissions,
  thermal behavior, cancellation, and lifecycle.
- Record test limitations and artifact expectations explicitly.

## Impact

- Adds CI configuration, instrumentation tests, and validation documentation.
- Does not attempt to emulate GPU or thermal behavior in CI.
- Should precede the structural Chat refactor in improvement 7.
