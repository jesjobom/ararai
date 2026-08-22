# Code-quality and security audit remediation register

Audit baseline: `044f66c` on 2026-08-17. Raw scanner and build artifacts remain
under the ignored `.audit/code-quality-security/2026-08-17-044f66c/` directory.
This register is the deduplicated, human-reviewed disposition of findings from
`improve`, Semgrep, CodeQL, and the manual platform and supply-chain reviews.

## Confirmed actionable findings

| Order | ID | Source | Severity | Confidence | Disposition | Follow-up change |
| ---: | --- | --- | --- | --- | --- | --- |
| 1 | ARA-AUD-001 | improve + manual | Low | High | Confirmed | `harden-model-path-containment` |
| 2 | ARA-AUD-002 | improve + manual | Low | High | Confirmed | `recover-unreadable-provider-credentials` |
| 3 | ARA-AUD-005 | improve + manual | Low | High | Confirmed | `close-model-http-resources` |
| 4 | ARA-AUD-004 | improve + manual | Low | High | Confirmed | `align-android-build-java-runtime` |
| 5 | ARA-AUD-003 | improve + Play observation | Low | High | Confirmed | `enable-safe-release-shrinking` |
| 6 | ARA-AUD-006 | improve + repository history | Low | High | Confirmed | `decompose-application-ui-hub` |

No critical or high-severity exploitable vulnerability was confirmed. The first
item is ordered highest because it is the only filesystem-containment weakness;
its current reachability is constrained to the reviewed bundled model catalog,
which limits severity to Low. Credential recovery follows because it can
leave a security-sensitive provider in a misleading enabled state. Explicit
HTTP ownership precedes build/DX and structural work because it affects runtime
resources. Runtime alignment precedes release shrinking so release evidence is
collected on a reproducible Java baseline. UI decomposition remains last so it
cannot obscure focused security and correctness corrections.

Each follow-up uses `ponytail` discipline: characterize the boundary first,
reuse existing abstractions, change the smallest coherent file set, avoid new
dependencies unless evidence requires one, and preserve validation and error
handling. Scanner or CI adoption is intentionally deferred until a later
measurement demonstrates stable signal, acceptable false-positive rate, and
acceptable execution cost.

## Non-actionable dispositions

| Candidate | Source | Disposition | Rationale |
| --- | --- | --- | --- |
| Web-search cancellation is swallowed | improve | False positive | Cancellation is deliberately serialized as `SEARCH_CANCELLED`, stops fallback, and is covered by unit and instrumentation tests. |
| Web-search endpoints permit arbitrary SSRF | improve | False positive | Production transport requires HTTPS and exact host/path allowlists, rejects query/userinfo/port, disables redirects, bounds responses, and applies timeouts. |
| Chat media deletion accepts arbitrary paths | improve | False positive | The repository canonicalizes candidates and owns only direct children of the app media root. |
| Firebase API key is a committed secret | Semgrep/manual | False positive | The value is public client configuration; anonymous Auth, App Check, and Firestore rules enforce authorization. No private signing or debug-token material was found. |
| Broad `Throwable` catches are vulnerabilities | Semgrep/improve | False positive | Reviewed generation and transcription paths preserve cancellation or guarantee cleanup; no concrete reachable impact was established. |
| Pending sideload report proves broken retry | manual | Accepted risk | Permanent App Check failure is intentionally non-retryable and device validation confirmed the distribution boundary. |
| Add an ArarAI-specific Semgrep rule | Semgrep | Accepted risk | No confirmed recurring project-specific pattern remained after triage, so a maintained custom rule would add noise without demonstrated signal. |
| Add CodeQL data extensions | CodeQL | Accepted risk | Official models covered the relevant extracted Android/JDK sources and sinks; the reviewed app wrappers did not expose an additional confirmed flow requiring a maintained model. |
| Run a separate C/C++ CodeQL audit now | CodeQL/manual | Accepted risk | Maintained JNI glue is small, while most native source is fetched third-party code; extraction cost is disproportionate and dependency provenance was reviewed separately. |

There are 6 deduplicated confirmed finding IDs, all actionable and linked to a
follow-up change. Separately, 22 distinct scanner results were fully triaged as
non-actionable: 2 Semgrep results and 20 distinct CodeQL rule/location results.
There are 0 unresolved/needs-investigation items. Duplicate scanner observations
were merged into the applicable disposition rather than assigned new IDs.
