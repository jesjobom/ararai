# Dependency update workflow

ArarAI locks resolved Gradle modules and verifies downloaded artifacts with
checked-in SHA-256 metadata. A version declaration alone is not a complete
dependency update.

## Update a dependency or build plugin

1. Change the reviewed version declaration.
2. Regenerate locks and verification metadata while running the complete graph:

   ```bash
   ./gradlew testDebugUnitTest spotlessCheck detekt lintDebug \
     assembleDebug assembleDebugAndroidTest \
     --write-locks --write-verification-metadata sha256
   ```

3. Review every changed module/version in `gradle.lockfile`,
   `app/gradle.lockfile`, and `whisper-runtime/gradle.lockfile`.
4. Review new or changed components and checksums in
   `gradle/verification-metadata.xml`. Check the artifact origin and release
   independently; never accept metadata solely because Gradle downloaded it.
5. Run `scripts/quality-gate.sh` normally. Dependency verification defaults to
   strict mode when the checked-in metadata is present.

Use Gradle's targeted `--update-locks group:artifact` only when intentionally
changing a subset of the graph. Do not hand-edit generated lockfiles.

## Update the Gradle wrapper

1. Select a reviewed Gradle version compatible with the Android Gradle Plugin.
2. Update the wrapper with Gradle's wrapper task.
3. Obtain the `-bin.zip.sha256` value from the official Gradle distribution
   service and update `distributionSha256Sum` in
   `gradle/wrapper/gradle-wrapper.properties`.
4. Run `./gradlew help --warning-mode all`; it must contain no deprecation
   warnings before the full quality gate.
5. Regenerate and review dependency locks and verification metadata if the
   wrapper or plugin graph changed.

## Boundaries

These controls cover Gradle-resolved artifacts and the wrapper distribution.
Android SDK/NDK packages, CMake-provisioned native sources, signing keys, and APK
provenance require separate controls. Release signing and distribution are not
enabled by this workflow.

