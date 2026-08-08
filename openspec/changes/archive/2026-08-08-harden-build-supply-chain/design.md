# Design: Verified and locked Gradle inputs

## Toolchain compatibility

The deprecated `io.gitlab.arturbosch.detekt` 1.x plugin is the confirmed caller
of `ReportingExtension.file(String)`. Migrate to the current `dev.detekt` plugin
line, update its DSL only where required, and require `help --warning-mode all`
to complete without Gradle deprecation output.

## Dependency locking

All resolvable project configurations participate in Gradle dependency locking.
Checked-in lockfiles record selected module versions while normal dependency
declarations remain the human-maintained intent. Updating dependencies requires
an explicit `--write-locks` run and review of the resulting graph diff.

## Cryptographic verification

Gradle dependency verification operates in strict mode using SHA-256 checksums
generated from the complete quality-gate dependency graph. Metadata is checked in
and changes require explicit regeneration plus review. Trusted keys are not
required for repositories/artifacts without consistently published signatures;
checksums provide deterministic artifact identity.

The wrapper uses Gradle's `distributionSha256Sum` with the checksum published by
the Gradle distribution service.

## Boundaries

This change authenticates downloaded Gradle artifacts and freezes selected module
versions. It does not make Android SDK/NDK packages, the vendored whisper source
download, release signing keys, or produced APK provenance reproducible. Those
belong to later native-source and signed-release changes.

