## 1. Specification

- [x] 1.1 Define Gradle compatibility, locking, verification, and update workflow requirements.

## 2. Gradle 10 compatibility

- [x] 2.1 Migrate Detekt to the supported plugin namespace/version.
- [x] 2.2 Prove project configuration emits no Gradle deprecation warnings.

## 3. Supply-chain controls

- [x] 3.1 Pin the wrapper distribution SHA-256 checksum.
- [x] 3.2 Enable dependency locking and check in locks for all resolved project configurations.
- [x] 3.3 Generate strict SHA-256 dependency verification metadata from the full quality gate.

## 4. Documentation and validation

- [x] 4.1 Document safe dependency and verification metadata updates.
- [x] 4.2 Run the complete project quality gate under strict verification.
- [x] 4.3 Run strict OpenSpec validation and record remaining release/native boundaries.
