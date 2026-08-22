## 1. Establish the audit baseline

- [x] 1.1 Record the audited commit, working-tree state, date, first-party scope,
  exclusions, expected source baseline, and run-specific ignored artifact paths.
- [x] 1.2 Verify and record the shared Semgrep and CodeQL executable paths and
  versions; do not install project-local scanner copies.
- [x] 1.3 Create the lightweight threat model and risk hypotheses for Android,
  local data/media, models, external content/tools, Firebase, background work,
  and JNI/native boundaries.
- [x] 1.4 Define the finding-register schema, severity/confidence rules,
  deduplication policy, dispositions, and evidence required to call a finding
  confirmed.

## 2. Run the broad read-only review

- [x] 2.1 Execute the `improve` survey across correctness, security, privacy,
  architecture, lifecycle/concurrency, performance, tests, dependencies, DX, and
  documentation.
- [x] 2.2 Verify each candidate against current source and tests, reject
  unsupported observations, and enter evidence-backed candidates in the common
  finding register.

## 3. Run and triage Semgrep

- [x] 3.1 Run the shared Semgrep 1.172.0 executable with recorded `p/default` and
  `p/security-audit` rulesets over first-party sources only; preserve commands,
  logs, resolved scope, and JSON or SARIF output.
- [x] 3.2 Manually triage every Semgrep result for ownership, reachability,
  sanitization, Android context, duplication, and existing tests; record a
  disposition and rationale for each.
- [x] 3.3 If a confirmed recurring ArarAI-specific pattern exists, create the
  smallest custom rule with positive/negative fixtures, validate its YAML, and
  require a fully passing `semgrep --test`; otherwise record why no custom rule
  was warranted.

## 4. Build and assess the CodeQL database

- [x] 4.1 Resolve a fresh CodeQL output directory and build a new Java/Kotlin
  database with CodeQL 2.26.2 and a real non-cached Gradle build, preserving the
  complete build log.
- [x] 4.2 Assess database quality by comparing extracted first-party files and
  baseline LoC with the expected scope and by investigating extractor errors;
  do not proceed with materially incomplete extraction.
- [x] 4.3 Evaluate project wrappers for data-extension modeling and either add
  validated extensions for relevant sources, sinks, sanitizers, and summaries or
  document the evidence for not adding them.

## 5. Run and triage CodeQL

- [x] 5.1 Inventory available official and compatible installed query packs and
  record included or explicitly excluded packs in `rulesets.txt`.
- [x] 5.2 Run an explicit high-precision security suite first, preserving raw and
  final SARIF, and triage every result into the common register.
- [x] 5.3 Run an explicit broad suite combining security-and-quality and
  security-experimental queries, preserve raw and final SARIF, and triage every
  new result without duplicating earlier findings.
- [x] 5.4 If either pass has zero findings, verify database quality, suite
  resolution, executed query count, and custom model coverage before accepting
  that result.
- [x] 5.5 Decide from maintained native-code scope and build cost whether a
  separate C/C++ CodeQL change is justified; do not mix fetched third-party
  native findings into the Java/Kotlin audit.

## 6. Review uncovered platform and supply-chain boundaries

- [x] 6.1 Perform the focused Android/Firebase review of manifest/components,
  intents/providers, permissions, storage/backup/logging, network/TLS/rendering,
  Keystore, App Check/Firestore, WorkManager, and foreground services.
- [x] 6.2 Review Gradle/npm/native/model inputs for locks, checksums, provenance,
  licenses, stale components, and reachable high/critical advisories.
- [x] 6.3 Reconcile manual findings with scanner and `improve` results in the
  common register.

## 7. Prioritize and prepare remediation

- [x] 7.1 Deduplicate the complete register, assign severity and confidence from
  evidence and reachability, and retain rationales for false positives, accepted
  risks, duplicates, and unresolved items.
- [x] 7.2 Create one bounded follow-up OpenSpec change per coherent confirmed
  remediation group, including expected regression evidence and device checks
  where automation cannot prove the boundary.
- [x] 7.3 Order follow-up changes by exploitable security risk, correctness/
  privacy/data-loss risk, lifecycle/resource ownership, boundary tests, measured
  performance, and structural debt.
- [x] 7.4 Record that implementation changes use `ponytail` discipline and that
  scanner/CI adoption requires demonstrated signal and acceptable execution cost.

## 8. Close the audit change

- [x] 8.1 Publish the final audit summary with commands, tool versions, scope,
  exclusions, database-quality evidence, raw artifact locations, register totals,
  coverage limitations, and links to follow-up changes.
- [x] 8.2 Confirm every raw finding has a register disposition and every confirmed
  actionable finding has an owner/follow-up change or explicit accepted-risk
  decision.
- [x] 8.3 Run strict OpenSpec validation and the repository quality gate before
  archiving this organizing change.
