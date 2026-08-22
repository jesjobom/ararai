## 1. Define recovery behavior

- [x] 1.1 Add characterization tests for missing, valid, malformed, and undecryptable stored credentials.
- [x] 1.2 Specify the user-visible recovery state and whether unusable ciphertext is removed immediately or only after explicit replacement.

## 2. Implement credential reconciliation

- [x] 2.1 Model credential readability explicitly without returning or logging secret contents in errors.
- [x] 2.2 Prevent an unreadable provider credential from satisfying configured/enabled gating.
- [x] 2.3 Surface actionable replacement guidance while preserving valid credentials and provider settings.

## 3. Validate

- [x] 3.1 Run focused unit and Android instrumentation tests, including malformed ciphertext and Keystore failure simulation.
- [x] 3.2 Verify recovery on a physical device if key invalidation cannot be faithfully automated.
- [x] 3.3 Run the complete quality gate and strict OpenSpec validation.
