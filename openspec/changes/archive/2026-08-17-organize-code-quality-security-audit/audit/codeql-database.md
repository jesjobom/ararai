# CodeQL database assessment

## Build

- Date: `2026-08-17`
- Baseline commit: `044f66c`
- Executable: `/home/node/.openclaw/jarvis/tools/codeql/2.26.2/codeql`
- Version: `2.26.2`
- Language: Java/Kotlin (`java` extractor)
- Database: `.audit/code-quality-security/2026-08-17-044f66c/codeql/codeql.db`
- Complete log: `.audit/code-quality-security/2026-08-17-044f66c/codeql/build.log`
- Build-log SHA-256: `e626513d88175ef41ba6aaac9255eec334e80885ad4c9735943bd981ffb14ba8`

The database was created from a clean, non-cached Gradle compilation:

```sh
/home/node/.openclaw/jarvis/tools/codeql/2.26.2/codeql database create \
  .audit/code-quality-security/2026-08-17-044f66c/codeql/codeql.db \
  --language=java \
  --source-root=. \
  --command='./gradlew --no-daemon --no-build-cache clean \
    :app:compileDebugKotlin \
    :app:compileDebugUnitTestKotlin \
    :whisper-runtime:compileDebugJavaWithJavac' \
  --overwrite
```

The explicit command avoids release-signing inputs while compiling all maintained
production/debug/runtime Kotlin plus the JVM unit-test source set. Forty-one
Gradle tasks executed and the build completed successfully in 2m31s. It was not
a cached or `build-mode=none` extraction. The release source set contains only
the provider-selection file analogous to the extracted debug source; release
behavior remains in manual/platform review scope.

## Quality assessment

| Metric | Result | Verdict |
| --- | ---: | --- |
| Baseline Java/Kotlin LoC | 28,731 | proportional to the 29,926-LoC maintained build scope |
| Expected maintained files | 163 | baseline inventory |
| Extracted maintained files | 163 | 100% |
| Missing expected files | 0 | complete |
| Unexpected first-party files | 0 | scope matched |
| Extractor errors | 0 | one telemetry note only |
| Extractor error ratio | 0.0% | below 5% gate |
| Database finalized | `true` | valid |

The expected and extracted file lists have identical SHA-256
`415650d891cf5649f202736839567065d11de0ef3d2f69e987d38d5917d1f3ca`.
The database is therefore accepted for analysis. Generated Android/XML and stale
native FetchContent paths may exist in the source archive, but they do not inflate
the Java/Kotlin first-party comparison and are excluded from finding ownership.

## Data-extension evaluation

No pre-existing CodeQL model pack or standalone data extension exists in the
repository. Diagnostic queries enumerated the sources and sinks already modeled
by `codeql/java-all`:

- four remote sources: the exported activity intent plus three network response
  streams in the web-search, Wikipedia, and model-download boundaries;
- 282 security-relevant sink nodes: 213 path, 66 SQL, and three SSRF sinks;
- zero command-execution, XSS, or XXE sinks, consistent with the application API
  surface.

The custom transport and storage wrappers were then cross-checked against source:

- `UrlConnectionWebSearchHttpTransport`,
  `UrlConnectionKnowledgeHttpTransport`, and `UrlModelByteSource` expose direct
  `HttpURLConnection` response streams already recognized as external sources;
  their first-party bodies and return paths are present in the database;
- SQLite and file operations resolve to direct Android/JDK sinks, including the
  calls behind `SqliteChatSessionStore`, `PendingReportQueue`, and
  `FileChatMediaRepository`;
- `FirestoreGeneratedContentReportTransport` is an outbound authenticated report
  boundary, not a SQL/command/path/SSRF sink hidden behind a project API;
- `Task.await`, response DTOs, repository interfaces, and coroutine wrappers have
  visible implementations, so normal interprocedural flow and Kotlin compiler
  output can model their pass-through behavior without global manual summaries;
- Compose text input and local model/chat state are not remote attacker sources in
  this single-user Android threat model. Marking every UI parameter as `remote`
  would manufacture flows outside the agreed risk model.

No missing source, sink, sanitizer, or summary model was demonstrated. Adding an
extension without such evidence would broaden taint indiscriminately and reduce
signal. Task 4.3 therefore exits at the workflow's documented no-gap condition;
no extension files were created or deployed into the shared CodeQL installation.

Raw diagnostic artifacts are retained under
`.audit/code-quality-security/2026-08-17-044f66c/codeql/diagnostics/`, including
the queries, BQRS/CSV outputs, source inventory, coverage comparison, and database
diagnostics. `sources.csv` has SHA-256
`90140571c94f0c1586095fa1f30b4264af3e52561438875f15441bcc232f66da`;
`sinks.csv` has SHA-256
`f6ce4e3ee2354485efc6faa1214fdc8c05e1edb333688a35c914a1e077e0a003`.
