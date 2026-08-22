# CodeQL analysis

## Selection and execution

- Date: `2026-08-17`
- Database: `.audit/code-quality-security/2026-08-17-044f66c/codeql/codeql.db`
- Database quality: 28,731 LoC, 163/163 maintained files, zero extractor errors
- Query pack: official `codeql/java-queries` `1.11.7`
- Compatible but unavailable packs: `trailofbits/java-queries` and
  `GitHubSecurityLab/CodeQL-Community-Packs-Java`
- Model packs: none; task 4.3 found no demonstrated model gap
- Threat models: default `remote` plus `local` and `file`
- Runtime limits: 12,000 MiB, four threads, 600,000 ms per query
- Selection record: `.audit/code-quality-security/2026-08-17-044f66c/codeql/rulesets.txt`

The user explicitly approved the installed-pack and threat-model selection. No
pack was installed or updated during the audit, so the run is tied to the
recorded shared CodeQL distribution rather than a network-resolved query set.

The high-precision suite explicitly loaded all official Java queries and then
selected security-tagged problem/path queries with high/very-high precision, or
medium precision subject to the required post-SARIF security-severity threshold.
It resolved 168 queries. The broad suite explicitly imported both
`java-security-and-quality.qls` and `java-security-experimental.qls`, resolved
296 unique queries, and reused cached evaluations from the first pass where
applicable. No query timed out or failed.

## High-precision results

- Raw SARIF findings: 9
- Post-filter findings: 8
- Unique rules: four
- Confirmed findings: 0
- Final SARIF SHA-256:
  `1bf34069cb8d3e35b24902f39a6676ab9efb4d58378bb756b385d0c378ba32e3`

The eight retained alerts were four non-SSL stream alerts, two backup alerts,
one implicit `PendingIntent`, and one local temporary-file disclosure. The ninth
raw result was a medium-precision result below the 6.0 security-severity gate and
was removed only from the final SARIF; the raw SARIF remains unchanged.

## Broad results

- Findings: 16
- Unique rules: six
- Confirmed findings: 0
- Final/raw SARIF SHA-256:
  `6dd96cc5614a6eb5b0f667338f57e9b8f4d939cd2cae9323c0e88436cefa4f97`

The broad pass reported the same implicit `PendingIntent`, two backup alerts,
and temporary-file alert, plus one missing-certificate-pinning alert, one field
masking alert, and ten unread-local alerts. Four important-only non-SSL alerts
were not emitted by the imported broad suites; their explicit high-precision
execution and dispositions are preserved rather than inferred from the broad
pass.

Across both final outputs there are 24 result instances and 20 distinct
rule/location results after four overlaps. Every distinct result has a
disposition below.

## Triage

### Implicit `PendingIntent` — false positive

CodeQL traced both notification actions but misclassified their intents as
implicit. `openAppIntent` constructs `Intent(this, MainActivity::class.java)`;
`cancelIntent` constructs `Intent(context, ModelDownloadService::class.java)`.
Both are component-explicit, and both `PendingIntent` calls set
`FLAG_IMMUTABLE`. The service is also non-exported. No unspecified recipient can
receive either capability.

### Backup enabled — excluded third-party source

Both alerts point into `.cxx/.../_deps/llama_cpp-src/examples/llama.android`, a
fetched upstream example excluded from first-party ownership and not packaged as
the ArarAI application manifest. The maintained manifest explicitly sets
`android:allowBackup="false"` and supplies restrictive backup/data-extraction
rules.

### Non-SSL connection — false positives

The web-search transport requires an HTTPS scheme, exact host and exact path,
rejects authority/query ambiguity, and disables redirects before opening the
connection. The Wikipedia transport likewise requires HTTPS and a constrained
Wikipedia API host/path and disables redirects. Model catalog validation requires
every primary/fallback model URL to begin with `https://`; Android configuration
also sets `usesCleartextTraffic="false"`. The rule keys on the common
`HttpURLConnection` type and does not prove a cleartext execution path.

The model downloader still permits automatic redirects and has an independently
recorded connection-ownership question (ARA-AUD-005). Redirect policy and
connection lifecycle remain manual platform-review subjects; they are not
silently treated as proven safe by this scanner disposition.

### Temporary-file disclosure — test-only false positive

The result is solely in `ChatAudioRecorderTest`, which uses the host JVM's
`File.createTempFile` for a three-byte fixture and is not shipped. Production
recording files are created under app-private directories on Android. This is not
a reachable release disclosure.

### Missing certificate pinning — non-actionable hardening signal

The app deliberately relies on Android's platform trust store and hostname
verification for fixed/constrained HTTPS services. Certificate pinning is not a
declared product guarantee and adds certificate-rotation/outage risk. The query
does not show bypass of TLS validation, a permissive trust manager, or hostname
verification failure. Reconsider pinning only if a future threat model requires
resistance to a compromised platform CA and an operational pin-rotation/recovery
process exists.

### Field masking and unread locals — Kotlin compiler artifacts

The field-masking result names Compose's synthetic `$stable` field at line zero.
The ten unread-local results likewise point to line-zero synthetic temporaries
for Kotlin sealed/data-object branches (for example `Completed`, `Started`,
`Valid`, and `TransientFailure`). They do not identify source-level fields or
locals that can be edited, and existing Kotlin/Detekt compilation reports no
equivalent issue.

## Zero-result and coverage controls

Neither pass produced zero findings, so task 5.4's zero-finding contingency did
not trigger. Its controls were nevertheless established: the accepted database
has complete 163/163 maintained-source coverage; explicit suites resolved 168
and 296 queries; the query logs show no timeout/failure; and the model evaluation
documented why no extension was added. The broad diagnostic message's 163 of 175
files is consistent with the 163 maintained inputs plus generated/build sources,
not a 12-file first-party gap.

Raw suites, resolved query lists, evaluator logs/summaries, analysis logs, and
SARIF files are retained under the ignored CodeQL artifact directory. They are
not committed because the database and evaluator cache are approximately build
artifacts, while this document retains the reviewed conclusions.

## Native CodeQL decision

A separate C/C++ CodeQL change is not justified at this stage. The maintained
native surface is one 178-line JNI translation unit plus a 42-line CMake file.
Creating a traced Android C++ database would primarily compile and extract the
much larger fetched `whisper.cpp`/GGML tree, contradicting the first-party scope
and making ownership triage expensive. The maintained wrapper remains explicitly
in section 6 manual review for WAV chunk-size/allocation bounds, JNI exception and
reference handling, `whisper_context` lifetime, integer conversions, and native
resource exhaustion. A separate C++ database should be reconsidered only if the
maintained native surface grows materially, a wrapper-specific signal appears,
or an upstream advisory requires reachability analysis.
