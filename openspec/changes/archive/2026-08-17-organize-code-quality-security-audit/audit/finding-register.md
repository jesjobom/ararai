# Finding register

## Classification rules

The register is the single source for triaged results from `improve`, Semgrep,
CodeQL, manual Android/Firebase review, and supply-chain review. Raw scanner
counts are not finding counts.

### Required fields

| Field | Meaning |
| --- | --- |
| ID | Stable `ARA-AUD-NNN` identifier |
| Sources | One or more of `improve`, `semgrep`, `codeql`, `manual`, `supply-chain` |
| Category | Correctness/security/privacy/data loss/concurrency/performance/tests/architecture/DX/docs; include CWE when applicable |
| Location | Current first-party file and line or exact configuration boundary |
| Evidence | Reproducible source/data flow, command, test, log, or operational observation |
| Reachability | Preconditions and whether the path is reachable in a shipped configuration |
| Impact | Concrete consequence to users, data, system, or delivery |
| Severity | Critical/high/medium/low/informational |
| Confidence | High/medium/low |
| Disposition | Confirmed/false positive/accepted risk/duplicate/needs investigation |
| Rationale | Required for every disposition, especially non-confirmed results |
| Validation | Expected failing test, reproduction, rescan, device check, or other closure proof |
| Follow-up | OpenSpec change, owner/decision, duplicate ID, or investigation action |

### Severity

- **Critical:** practical compromise of release/build integrity, arbitrary code or
  privileged action, broad sensitive-data disclosure, or destructive impact with
  plausible preconditions.
- **High:** reachable authorization/security boundary bypass, significant private
  data disclosure/loss, persistent corruption, or severe remotely triggerable
  denial of service.
- **Medium:** bounded security/privacy/correctness impact requiring meaningful
  preconditions, or important lifecycle/resource failure without broad impact.
- **Low:** limited impact, defense-in-depth weakness, narrow reliability issue, or
  maintainability defect with a credible failure path.
- **Informational:** useful hardening/coverage observation without a demonstrated
  defect.

Severity is assigned after reachability and existing controls are evaluated; a
scanner label is retained as source metadata but does not determine final
severity.

### Confidence

- **High:** directly demonstrated or follows from a complete reviewed path with
  no material unknown boundary.
- **Medium:** strong source evidence with one unverified runtime/configuration
  assumption.
- **Low:** plausible hypothesis requiring more source, runtime, or operational
  evidence.

Only `confirmed` entries with medium/high confidence enter the remediation
backlog. Low-confidence entries remain `needs investigation`.

### Deduplication and disposition

- Multiple tools describing the same root cause share one ID and list all
  sources; child symptoms are referenced in its evidence.
- `False positive` requires the controlling sanitizer, invariant, unreachable
  configuration, or misunderstood source ownership to be identified.
- `Accepted risk` requires the owner, reason, compensating control, and review
  trigger/date where relevant.
- `Duplicate` names the canonical ID.
- `Needs investigation` names the missing evidence and next action.
- Tool absence of findings is recorded as coverage evidence, never as a finding
  or proof of security.

## Register

| ID | Sources | Category | Location | Severity | Confidence | Disposition | Summary | Follow-up |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ARA-AUD-001 | improve | Security/correctness | `ModelConfigParser.kt:163`, `ModelFileDownloader.kt:102`, `ModelResolver.kt:27` | Low | High | Confirmed | `models/` prefix validation does not canonically prevent configured path escape. | `harden-model-path-containment` |
| ARA-AUD-002 | improve | Correctness/security hardening | `WebSearchPreferences.kt:161-195` | Low | High | Confirmed | Unreadable encrypted tokens remain classified as configured/enabled. | `recover-unreadable-provider-credentials` |
| ARA-AUD-003 | improve | Build/performance/security hardening | `app/build.gradle.kts:93` | Low | High | Confirmed | Release shrinking is disabled and no R8 compatibility/mapping gate exists. | `enable-safe-release-shrinking` |
| ARA-AUD-004 | improve | DX/build correctness | `.github/workflows/android-quality-gate.yml:29-33`, `docs/quality-gates.md:33` | Low | High | Confirmed | CI executes Java 21 while naming/documenting the Android gate as JDK 17. | `align-android-build-java-runtime` |
| ARA-AUD-005 | improve, manual | Reliability/performance | `ModelFileDownloader.kt:43-73` | Low | High | Confirmed | The URL adapter owns an `HttpURLConnection` but non-2xx setup paths do not close the error stream or explicitly disconnect it. | `close-model-http-resources` |
| ARA-AUD-006 | improve | Architecture/testability | `ArarAiApp.kt` | Low | High | Confirmed | 2,011-line highest-churn UI hub still combines multiple destinations and suppresses complexity. | `decompose-application-ui-hub` |

## Stage reconciliation

Update this section after every audit stage.

| Stage | Raw results | New register entries | Merged duplicates | Dispositions complete | Artifact/evidence location |
| --- | ---: | ---: | ---: | --- | --- |
| Baseline and threat model | 0 | 0 | 0 | Yes | `audit/` |
| `improve` standard review | 6 vetted | 6 | 0 | Yes | `plans/README.md` |
| Semgrep default + security audit | 2 | 0 | 0 | Yes — 2 false positives | `audit/semgrep.md`, ignored raw JSON |
| CodeQL important-only + broad | 9 + 16 | 0 | 4 overlapping results | Yes — 20 distinct results triaged | `audit/codeql-analysis.md`, ignored SARIF/logs |
| Android/Firebase + supply chain | 1 existing candidate confirmed | 0 | 1 reconciled with `ARA-AUD-005` | Yes | `audit/platform-supply-chain.md`, ignored resolution/audit logs |

## Improve rejected candidates

The following reviewed signals were not entered as findings: serialized web-tool
cancellation, arbitrary web-search SSRF, arbitrary Chat-media deletion, the
public Firebase client API key, blanket treatment of `Throwable` catches, and
permanent failure for an unlicensed sideload. Evidence and rationale are retained
in `plans/README.md` so later tools do not automatically resurrect them without
new data.

## Semgrep rejected candidates

The AES-GCM audit alert was rejected because encryption uses the Android
provider-generated randomized IV and stores it only for matching decryption. The
dynamic `urllib` alert was rejected because the script enforces an exact constant
HTTPS endpoint allowlist before the call. Partial parser regions and the taint
fixpoint timeout were manually reviewed; see `audit/semgrep.md`.

## CodeQL rejected candidates

CodeQL's implicit-`PendingIntent` alert was rejected because both intents are
component-explicit and immutable. Two backup alerts belong to excluded fetched
example manifests while ArarAI disables backup. Four non-SSL alerts do not cross
the HTTPS validators/platform cleartext prohibition. The temporary-file result
is a host-JVM test fixture. Missing certificate pinning is not a violated product
control and no TLS validation bypass was found. Field masking and ten unread
locals refer to line-zero Kotlin/Compose compiler artifacts. Full evidence and
the review triggers retained for redirect/pinning/native scope are in
`audit/codeql-analysis.md`.

## Manual platform and supply-chain reconciliation

The focused review confirmed the existing HTTP resource-ownership candidate as
`ARA-AUD-005` and did not require a new ID. Android manifest/export, permission,
backup, cleartext, intent, storage, logging, App Check, Firestore Rules,
WorkManager, native-wrapper, Gradle/npm/native/model provenance, checksum, and
license boundaries were reviewed. Available evidence found no reachable
high/critical dependency advisory; the absence of an approved Gradle/native
advisory feed is retained as a coverage limitation, not a claim of safety. See
`audit/platform-supply-chain.md`.
