## 1. Privacy policy

- [x] Decide whether preferences alone may be backed up or all app backup is disabled.
- [x] Inventory database, media, model, cache, and temporary-file locations.

## 2. Implementation

- [x] Add Android backup and data-extraction rules for every supported platform path.
- [x] Wire manifest attributes to the rules or disable backup according to the decision.
- [x] Exclude private, large, derived, and reference-sensitive app data.
- [x] Document backup and device-transfer behavior.

## 3. Validation

- [x] Add static manifest/resource verification where practical.
- [ ] Perform a device backup/restore or transfer check on a supported physical device (no ADB device connected in this environment).
- [x] Run `./gradlew testDebugUnitTest`.
- [x] Run `./gradlew lintDebug`.
- [x] Run `./gradlew assembleDebug`.
- [x] Run `openspec validate melhoria-5-proteger-dados-de-backup --strict`.
