# Semgrep audit

## Execution

- Date: `2026-08-17`
- Baseline commit: `044f66c`
- Executable: `/home/node/.openclaw/jarvis/tools/uv-tools/semgrep/bin/semgrep`
- Version: `1.172.0`
- Rulesets: `p/default`, `p/security-audit`
- Engine: Semgrep OSS
- Raw artifacts:
  - `.audit/code-quality-security/2026-08-17-044f66c/semgrep/results.json`
  - `.audit/code-quality-security/2026-08-17-044f66c/semgrep/test-results.json`

Primary invocation:

```sh
/home/node/.openclaw/jarvis/tools/uv-tools/semgrep/bin/semgrep \
  --config p/default \
  --config p/security-audit \
  --json \
  --json-output .audit/code-quality-security/2026-08-17-044f66c/semgrep/results.json \
  --exclude '**/build/**' \
  --exclude '**/.gradle/**' \
  --exclude '**/.cxx/**' \
  --exclude '**/node_modules/**' \
  --exclude '.audit/**' \
  app/src/main/java app/src/debug/java app/src/release/java \
  whisper-runtime/src/main/java whisper-runtime/src/main/cpp \
  scripts tests firestore.rules app/build.gradle.kts build.gradle.kts \
  settings.gradle.kts
```

The registry supplied 1,087 available community rules. Semgrep selected and ran
311 rules applicable to 108 tracked targets: 99 Kotlin files, five Bash scripts,
one Python script, and multilang targets. The scan reported approximately 99.6%
parsed lines, two raw findings, two partial-parsing warnings, and one taint
fixpoint warning.

A second invocation targeted `app/src/test` and `app/src/androidTest`. Semgrep's
default ignore policy excluded 69 JVM unit-test files; eight Android test/manifest
targets were scanned with 179 applicable rules, 100% parsed lines, zero findings,
and zero errors. This is acceptable for this security pass because JVM fixtures
are not shipped runtime code; their relevant role remains proof for production
boundaries. The exclusion is recorded rather than misreported as test coverage.

Artifact checksums:

- `results.json`: `19108f706cfa2f6a96af1664f33c9dc84805ddade5eb5074477b0c2c70d0f40f`
- `test-results.json`: `6ef17720c0a679c661bb1e837e790f04b9dce233bb3321c933e0a47fd00ab727`

Registry rules are network-resolved and were not vendored. Reproduction therefore
requires the recorded Semgrep version/ruleset names and may still observe future
registry drift.

## Finding triage

### GCM detection — false positive

- Rule: `kotlin.lang.security.gcm-detection.gcm-detection`
- Location: `WebSearchPreferences.kt:226`
- Raw severity: informational
- Disposition: false positive

The rule flags use of AES-GCM for manual nonce review; it does not establish nonce
reuse. Encryption initializes `AES/GCM/NoPadding` without caller-supplied IV at
`WebSearchPreferences.kt:216-219`, so Android's provider generates a randomized
IV. The IV is stored with the ciphertext. Decryption at `:222-227` decodes that
stored IV solely to read the corresponding ciphertext. The Keystore key requires
randomized encryption at `:235-244`. No reused fixed nonce was found.

This result reinforces existing ARA-AUD-002: decryption failures are silently
converted to `null` while ciphertext presence remains classified as configured.
It does not add a new cryptographic finding.

### Dynamic urllib use — false positive

- Rule: `python.lang.security.audit.dynamic-urllib-use-detected.dynamic-urllib-use-detected`
- Location: `scripts/run-direct-web-search-comparison.py:58`
- Raw severity: warning
- Disposition: false positive

`RequestBudget.post` checks the exact URL against a constant three-entry HTTPS
allowlist at `scripts/run-direct-web-search-comparison.py:23-27` and `:35-37`
before constructing the request and calling `urlopen`. Call sites pass those
same constants. A caller cannot select `file:`, another scheme, host, path, query,
or arbitrary remote endpoint through this boundary.

Adding the `requests` dependency only to silence this generic rule would increase
supply-chain surface without improving this fixed-endpoint script.

## Coverage warnings

- Semgrep partially parsed the Elvis expression following the token lookup at
  `WebSearchProviderTools.kt:26` and `:136`. These exact regions were manually
  inspected: they trim the credential and return `AuthenticationFailed` when it
  is absent; the token is then attached only to the fixed provider request.
- Semgrep's Bash parser reported `scripts/build-release-bundle.sh` as partially
  parsed, likely around Bash-specific strict mode/arithmetic/arrays. Manual review
  confirmed that signing material is read only from the external fixed Jarvis
  secret path, file modes are checked before use, secret values are not printed,
  Gradle builds the release, and the AAB signature is verified before copying.
- Taint analysis hit one fixpoint timeout in
  `scripts/run-direct-web-search-comparison.py:170` for a generic hardcoded-token
  rule. Manual inspection found tokens sourced from environment variables; no
  secret value or hardcoded provider credential was found.

These warnings mean the result is not literally full-parser coverage. They were
resolved through bounded manual review rather than ignored.

## Custom-rule decision

No custom Semgrep rule is warranted in this round.

- ARA-AUD-001 is a semantic filesystem ownership requirement: a raw
  `File(root, relativePath)` expression is safe only when normalization and
  canonical containment have already been proven. A simple syntactic rule would
  flag safe call sites or miss equivalent constructors.
- ARA-AUD-002 is a state-consistency bug across decryption and configured-provider
  classification, not a recurring dangerous syntax.
- The remaining `improve` findings are build configuration, resource ownership,
  or architecture concerns better covered by tests/manual review/CodeQL.

Creating an unproven rule would violate the skill's requirement for a specific
recurring pattern with positive and negative fixtures. This decision can be
revisited after CodeQL and manual reconciliation if a repeated source/sink shape
is confirmed.

## Result

- Raw findings: 2
- Confirmed new findings: 0
- False positives: 2
- Existing findings reinforced: ARA-AUD-002
- Custom rules added: 0
- Source changes: none
