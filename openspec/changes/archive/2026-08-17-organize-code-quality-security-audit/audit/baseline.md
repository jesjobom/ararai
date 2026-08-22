# Audit baseline

## Identity

- Captured at: `2026-08-17T02:49:52Z`
- Baseline commit: `044f66ce49afab7040b0298e96636164bb147d00`
- Baseline commit subject: `chore: finalize Play reporting release`
- Branch: `main`, synchronized with `origin/main` when the audit was opened
- Pre-existing working-tree changes: none
- Expected planning change: `openspec/changes/organize-code-quality-security-audit/`
- Raw artifact root: `.audit/code-quality-security/2026-08-17-044f66c/`

The raw artifact root is ignored by Git. Maintained audit conclusions and
follow-up decisions belong in this OpenSpec change; databases, logs, resolved
rules, JSON, CSV, and SARIF remain under the ignored run root.

## Shared tools

| Tool | Canonical executable | Verified version | Executable SHA-256 |
| --- | --- | --- | --- |
| Semgrep | `/home/node/.openclaw/jarvis/tools/uv-tools/semgrep/bin/semgrep` | `1.172.0` | `890106f2b6222a58a52f3a7398d359bff257f95b0808d5d4094ecee5439f7518` |
| CodeQL | `/home/node/.openclaw/jarvis/tools/codeql/2.26.2/codeql` | `2.26.2` | `5e459057abea0f2401d8f3a0eb7b4026571b17b8b5bb051ee66496386282dd27` |

These are shared host installations, not application dependencies. Each scan
must still record its exact command, selected rules/query packs, timestamps, and
network-resolved inputs. `rg` is not installed in the current runtime; baseline
inventory used `find` and `awk` without adding a repository dependency.

## First-party scope

The primary maintained scope is:

- `app/src/main/java`, Android manifests, resources, and build configuration;
- debug/release App Check provider selection under `app/src/{debug,release}`;
- unit and instrumentation tests under `app/src/{test,androidTest}`;
- application-owned Whisper JNI wrapper under `whisper-runtime/src`;
- Firestore rules and emulator tests (`firestore.rules`, `tests/`, Firebase
  configuration);
- root/module Gradle files, locks, verification metadata, ProGuard rules, and
  repository scripts;
- privacy, operations, quality, and device-validation documentation when a
  finding concerns a documented security or delivery guarantee.

Snapshot counts for extraction/scanner sanity checks:

| Scope | Files | Relevant source LoC |
| --- | ---: | ---: |
| `app/src/main/java` | 93 | 19,287 |
| `app/src/debug/java` | 1 | 12 |
| `app/src/release/java` | 1 | 12 |
| `app/src/test/java` | 68 | 10,667 |
| `app/src/androidTest/java` | 7 | 1,417 |
| `whisper-runtime/src/main/java` | 1 | 47 |
| `whisper-runtime/src/main/cpp` | 2 | 178 |
| `scripts` | 6 | 413 |
| `tests` | 1 | 211 |

The inventory contains 170 Kotlin files, one Java file, one maintained C++
translation unit plus its CMake file, one Firebase rules test, and six repository
scripts. Counts are comparison anchors, not absolute CodeQL extraction targets:
generated Android bindings and dependency code must not inflate apparent
first-party coverage.

## Explicit exclusions

Exclude these from first-party findings and scanner scope:

- `.git/`, `.gradle/`, all `build/` and `.cxx/` trees;
- `node_modules/` and package-manager caches;
- generated sources, compiled outputs, APK/AAB files, logs, and `.audit/`;
- downloaded model artifacts and runtime caches;
- CMake FetchContent copies of llama.cpp, whisper.cpp, LiteRT-LM, and other
  upstream code;
- third-party dependencies merely present in Gradle/npm caches.

Third-party code remains in supply-chain scope through version, provenance,
checksum, license, advisory, and reachability review. A defect in upstream code
is not recorded as a first-party source finding.

## Baseline commands

```sh
git status --short --branch
git rev-parse HEAD
/home/node/.openclaw/jarvis/tools/uv-tools/semgrep/bin/semgrep --version
/home/node/.openclaw/jarvis/tools/codeql/2.26.2/codeql version
find app/src whisper-runtime/src scripts tests -type f \
  ! -path '*/build/*' ! -path '*/.cxx/*'
```

Each later stage must write its exact invocation and results beneath the raw
artifact root and summarize only reviewed evidence in tracked OpenSpec files.
