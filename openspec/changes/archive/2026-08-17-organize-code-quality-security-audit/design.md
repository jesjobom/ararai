## Context

The existing `scripts/quality-gate.sh` covers formatting, Detekt, Android Lint,
tests, builds, and strict OpenSpec validation. Those checks are valuable but do
not replace architectural review, application-specific threat modeling,
pattern-based security analysis, or interprocedural data-flow analysis.

The audit must distinguish first-party sources from generated output, build
artifacts, fetched native dependencies, and third-party code. It must also avoid
reporting scanner output as fact before reachability and exploitability are
verified. Because the expected result is a set of findings with potentially very
different scopes, this organizing change will not contain their implementations.

## Goals / Non-Goals

**Goals:**

- Produce a reproducible, evidence-backed review of correctness, security,
  maintainability, performance risks, tests, and supply-chain boundaries.
- Preserve raw tool output while maintaining a separate manually triaged finding
  register.
- Prioritize confirmed findings consistently and turn actionable work into small
  follow-up OpenSpec changes.

**Non-Goals:**

- Claim that zero scanner findings proves the application secure.
- Fix, refactor, add dependencies, or change runtime behavior during the audit.
- Treat generated/fetched third-party sources as first-party findings.
- Add Semgrep or CodeQL to CI before their signal, cost, and false-positive rate
  are measured.

## Decisions

### Fix the audit baseline and artifact boundary

Record the audited commit, working-tree state, tool versions, commands, scope,
exclusions, and timestamps. Store generated scan artifacts in ignored,
run-specific directories so they cannot be confused with maintained source or
accidentally committed. Preserve raw SARIF/JSON and logs alongside a separate
human-reviewed finding register.

The first-party scope includes maintained Kotlin/Java, Android resources and
manifests, Gradle/build scripts, Firebase rules/tests, repository scripts, and
application-owned JNI/C++ code. Exclude `build/`, `.gradle/`, `.cxx/`, generated
sources, downloaded models, `node_modules/`, and fetched third-party native
trees. Dependency and native provenance are reviewed separately as supply-chain
inputs.

### Start with context and hypotheses

Create a concise threat model around local conversations and media, model
downloads and integrity, untrusted model/web content, files and intents,
credentials and Android Keystore, Firebase Auth/App Check/Firestore, background
work, exported components, and JNI/native memory boundaries.

Run `improve` as the broad read-only review before scanners. Its output supplies
concrete hypotheses and hotspots, but every item still requires source evidence
and manual confirmation.

### Use Semgrep for fast pattern-based coverage

Use the shared executable at
`/home/node/.openclaw/jarvis/tools/uv-tools/semgrep/bin/semgrep`, verify version
`1.172.0`, and record the resolved rulesets. Start with `p/default` and
`p/security-audit` against first-party paths and produce machine-readable output.
Registry rules require network access and their resolved identity must be
recorded for reproducibility.

Triage each result for source ownership, reachability, sanitization, Android
context, duplicates, and existing test coverage. Create a project-specific rule
only for a confirmed recurring pattern; such a rule requires positive and
negative fixtures and a passing `semgrep --test` run.

### Use CodeQL for interprocedural Java/Kotlin analysis

Use the shared executable at
`/home/node/.openclaw/jarvis/tools/codeql/2.26.2/codeql`, verify version `2.26.2`,
and create a new run-specific output directory. Build a fresh Java/Kotlin
database with a real uncached Gradle build. A successfully created database is
not sufficient: compare extracted source/file and LoC counts with the expected
first-party baseline and investigate extractor errors before analysis.

Evaluate application wrappers as possible data-extension sources, sinks,
sanitizers, or summaries, especially around intents, files, network tools,
model downloads, persistence, and Firebase. Record either the extensions or an
explicit evidence-based reason none are needed.

Run high-precision security analysis first, then an explicit combined
security-and-quality plus security-experimental suite. Never rely on an implicit
pack default suite. Preserve build logs, diagnostics, ruleset inventory, raw
SARIF, and final SARIF. Investigate zero findings by checking database quality,
suite selection, and model coverage.

CodeQL C/C++ analysis is a separate optional follow-up only if the maintained
native surface justifies its additional build/extraction cost; downloaded
whisper.cpp/llama.cpp sources are dependency-review scope rather than
first-party findings.

### Add reviews scanners cannot establish alone

Manually review Android and Firebase boundaries against relevant OWASP MASVS
themes: component export, intents, permissions, providers, backup, local storage,
logs, TLS/network configuration, Web/Markdown rendering, Keystore use, App Check,
Firestore rules, WorkManager, and foreground services. Review Gradle, npm used by
Firebase rule tests, native artifacts, and downloadable models for pinning,
checksums, provenance, licenses, and reachable known vulnerabilities.

### Maintain one triaged finding register

Normalize all sources into one register containing identifier, source,
category/CWE where applicable, severity, confidence, affected location, evidence,
reachability, impact, recommended boundary, test expectation, and disposition.
Merge duplicates across `improve`, Semgrep, CodeQL, and manual review. Allowed
dispositions are confirmed, false positive, accepted risk, duplicate, or needs
investigation; every non-confirmed disposition requires a short rationale.

Prioritize confirmed work in this order: exploitable critical/high security
issues; correctness, privacy, and data-loss risks; lifecycle/concurrency and
resource ownership; missing boundary tests; measured performance problems; then
structural debt. Severity alone does not override reachability evidence.

### Separate audit from remediation

Each coherent confirmed remediation becomes a separate OpenSpec change with a
focused failing test or other explicit proof where practical. Apply `ponytail`
during implementation to reuse existing boundaries, minimize changed files and
dependencies, and fix the common cause without weakening validation or error
handling.

Only after remediation and rescanning should checks be considered for CI.
Semgrep may become a pull-request gate if findings are stable and low-noise;
full CodeQL is more likely to be scheduled or hosted due to extraction cost.

## Risks / Trade-offs

- Broad scanner suites can generate noise; manual triage and a single register
  add time but prevent misleading work.
- Registry-backed Semgrep rules can drift; recording tool version, rulesets, and
  raw output improves traceability but does not fully vendor external rules.
- CodeQL extraction of an Android/Kotlin/native build can be expensive; database
  quality gates avoid spending analysis time on an incomplete database.
- Splitting remediation into later changes delays fixes discovered during the
  audit, but keeps evidence review distinct from implementation and prevents one
  unreviewable mega-change. A confirmed critical issue may interrupt the audit
  for an explicitly approved emergency change.

## Validation

- Both shared executables exist and report their expected versions.
- OpenSpec strict validation passes for this organizing change.
- Every planned audit stage specifies its inputs, output evidence, exclusions,
  and completion criterion.
- The final audit report reconciles every raw finding into the triaged register,
  documents coverage limitations, and creates or links follow-up changes for all
  confirmed actionable findings.
