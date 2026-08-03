# Change: Harden build supply chain

## Why

The current Detekt plugin invokes a Gradle API removed in Gradle 10, so a future
wrapper upgrade would break project configuration. Build dependencies are pinned
by declared version but are not locked as a resolved graph or authenticated with
checked-in checksums. The Gradle wrapper distribution also lacks its supported
SHA-256 pin.

## What Changes

- Migrate Detekt to the supported plugin namespace/version and eliminate all
  project-originated Gradle 10 deprecation warnings.
- Enable dependency locking for resolvable configurations and check in the
  resolved lock state for application, test, lint, plugin, and native modules.
- Enable strict Gradle dependency verification with checked-in SHA-256 metadata.
- Pin the Gradle wrapper distribution with its official SHA-256 checksum.
- Document the intentional update workflow for dependency versions, locks, and
  verification metadata.

## Impact

- Affected specs: `build-delivery`
- Affected code: Gradle plugin configuration, wrapper configuration, dependency
  locks, verification metadata, and build documentation.
- Release signing and distribution remain a separate future change.

