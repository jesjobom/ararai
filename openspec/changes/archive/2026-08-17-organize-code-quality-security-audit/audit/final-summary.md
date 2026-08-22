# Final code-quality and security audit summary

## Outcome

The read-only audit of baseline `044f66ce49afab7040b0298e96636164bb147d00`
confirmed six low-severity, high-confidence actionable findings. No critical or
high-severity exploitable vulnerability was confirmed. Each finding has one
bounded follow-up OpenSpec change:

1. `harden-model-path-containment` — ARA-AUD-001
2. `recover-unreadable-provider-credentials` — ARA-AUD-002
3. `close-model-http-resources` — ARA-AUD-005
4. `align-android-build-java-runtime` — ARA-AUD-004
5. `enable-safe-release-shrinking` — ARA-AUD-003
6. `decompose-application-ui-hub` — ARA-AUD-006

The order reflects reachability and impact: filesystem containment, encrypted
credential state, runtime resource ownership, reproducible build runtime,
release hardening, then structural debt. Implementations must use the recorded
`ponytail` discipline. Semgrep/CodeQL CI adoption remains deferred until signal,
false-positive rate, and execution cost are measured after remediation.

## Baseline, scope, and exclusions

- Captured: `2026-08-17T02:49:52Z`, branch `main`, initially synchronized with
  `origin/main`, with no pre-existing working-tree changes.
- First-party scope: maintained Android Kotlin/Java, manifests/resources/build
  configuration, tests, application-owned Whisper JNI/CMake, Firebase rules and
  tests, Gradle/npm inputs, repository scripts, and relevant documentation.
- Exclusions: generated/build output, `.gradle/`, `.cxx/`, `node_modules/`,
  downloaded models, caches, raw audit artifacts, and fetched third-party native
  source. Dependencies remained in provenance/license/advisory scope.
- Full inventory, commands, counts, and exclusion rationale:
  `audit/baseline.md` and `audit/threat-model.md`.

## Tools, commands, and evidence

- Semgrep `1.172.0` at the shared pinned executable, using `p/default` and
  `p/security-audit`. The exact invocation is in `audit/semgrep.md`.
- CodeQL `2.26.2` at the shared pinned executable, official
  `codeql/java-queries` `1.11.7`, with explicit important-only and broad suites.
  Database creation, suite commands, resolved queries, and resource policy are
  in `audit/codeql-database.md`, `audit/codeql-analysis.md`, and ignored logs.
- Broad review and platform/supply-chain evidence are in `plans/README.md` and
  `audit/platform-supply-chain.md`.
- Raw artifact root:
  `.audit/code-quality-security/2026-08-17-044f66c/` (intentionally Git-ignored).

## Scanner and database results

- Semgrep: 311 applicable rules over 108 primary targets, 2 raw results, both
  manually rejected as false positives; the separate Android-test pass found 0.
  Parser/fixpoint warnings were manually reviewed and retained as limitations.
- CodeQL database: finalized at 28,731 LoC with 163/163 expected maintained
  Kotlin/Java files extracted, 0 missing, 0 unexpected, and no material extractor
  error.
- CodeQL important-only: 168 resolved queries, 9 raw and 8 post-filter results.
- CodeQL broad: 296 resolved queries and 16 results. Across final outputs there
  were 20 distinct rule/location results after 4 overlaps; all were rejected or
  accepted as non-actionable hardening signals with rationale.
- Supply chain: npm reported 0 advisories for 724 packages; reviewed Gradle,
  native, and model inputs had locks/checksums/provenance/license controls and no
  demonstrated reachable high/critical advisory.

## Reconciliation totals

- Confirmed actionable finding IDs: 6
- Severity/confidence: 6 Low / 6 High confidence
- Follow-up OpenSpec changes: 6
- Distinct scanner results with disposition: 22 (2 Semgrep, 20 CodeQL)
- Unresolved or needs-investigation items: 0
- Confirmed findings without owner/follow-up: 0

The canonical evidence and disposition for every finding is in
`audit/finding-register.md`; the implementation order and summarized rejected
signals are in `plans/audit-remediation-register.md`.

## Coverage limitations

- Registry-backed Semgrep rules can drift and several Kotlin/Bash regions needed
  manual review after partial parsing or fixpoint warnings.
- CodeQL covered maintained Java/Kotlin completely but did not build a separate
  C/C++ database; the small maintained JNI boundary was reviewed manually while
  fetched native code remained supply-chain scope.
- Compatible community CodeQL packs were not installed; only the recorded
  official pack was used. No project data extension was warranted by observed
  flows.
- No approved Gradle/native advisory feed supplied complete reachability data;
  absence of a demonstrated advisory is not a clean bill of health.
- Static analysis cannot prove physical-device, Play signing, Firebase Console,
  GPU/vendor, memory, thermal, release-shrinking, or Keystore-invalidation
  behavior. The applicable follow-up changes require device evidence.

These limitations and zero confirmed scanner vulnerabilities do not establish
that ArarAI is vulnerability-free; they define the reproducible coverage of this
audit and the triggers for future review.
