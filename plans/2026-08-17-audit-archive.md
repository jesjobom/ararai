# Improve audit index

Baseline: `044f66c` (`2026-08-17`). This index is the read-only `improve`
deliverable for the active OpenSpec change
`organize-code-quality-security-audit`. It contains vetted candidates, not
implementation authorization. Remediation plans will be written only after the
full Semgrep, CodeQL, platform, and supply-chain rounds are reconciled.

## Recon

- Android/Kotlin application with Compose, local SQLite/media/model storage,
  LiteRT-LM, application-owned whisper JNI, Firebase reporting, and optional web
  knowledge tools.
- Canonical verification: `scripts/quality-gate.sh`; device-only boundaries are
  documented in `docs/device-validation.md`.
- Current tests cover model download/integrity, Chat/Voice state, media,
  persistence, tools, Firebase rules, Compose journeys, and build configuration.
- Highest current churn is in `ArarAiApp.kt`, `ChatViewModel.kt`, `ChatScreen.kt`,
  model configuration, and their tests.
- npm audit result on 2026-08-17: 0 advisories across the 724-package Firebase
  test-tool graph. Gradle/native/model advisory review remains a later task.

## Vetted findings

| Priority | ID | Finding | Category | Impact | Effort | Fix risk | Confidence |
| ---: | --- | --- | --- | --- | --- | --- | --- |
| 1 | ARA-AUD-001 | Canonically constrain configured model paths | Security/correctness | A malformed catalog path such as a `models/` prefix followed by traversal can resolve outside the intended model directory and be used by download, migration, resolution, or deletion code. The catalog is bundled and reviewed, so this is a defense against configuration/supply-chain mistakes rather than a remote arbitrary-path primitive. | S | Low | High |
| 2 | ARA-AUD-002 | Reconcile unreadable encrypted provider credentials | Correctness/security hardening | A stored token whose Keystore key/ciphertext can no longer be decrypted remains classified as configured and can remain enabled, while reads return no credential. Users then see persistent authentication failure rather than an explicit recoverable credential state. | S–M | Medium | High |
| 3 | ARA-AUD-003 | Validate and enable release shrinking safely | Build/performance/security hardening | Release builds explicitly disable R8, matching the Play Console's low-optimization/no-mapping warning. The app ships larger bytecode and gains no obfuscation/optimization; enabling it without release tests could break reflection/JNI/Firebase paths. | M | Medium | High |
| 4 | ARA-AUD-004 | Align the documented and exercised Java runtime | DX/build correctness | CI labels its setup JDK 17 but actually runs the full gate on Java 21, while project/quality documentation says JDK 17. Local and CI builds therefore do not exercise the same claimed Gradle runtime, weakening reproducibility and making failures environment-dependent. | S | Low | High |
| 5 | ARA-AUD-005 | Make model HTTP connection ownership explicit | Reliability/performance | `UrlModelByteSource` creates `HttpURLConnection` instances but never explicitly disconnects them; non-2xx paths throw before an input stream can be closed. Repeated fallback/failure cycles can retain network resources longer than necessary. | S–M | Low | Medium |
| 6 | ARA-AUD-006 | Continue decomposing the application UI hub | Architecture/testability | `ArarAiApp.kt` is 2,011 lines, has the highest recent churn, owns navigation plus several complete destination screens, and suppresses complexity/parameter findings. Prior work successfully extracted Chat/media boundaries, but unrelated settings, tools, benchmark, model, and shell changes still collide in one file. | L | Medium | High |

### ARA-AUD-001 evidence

- `app/src/main/java/com/jesjobom/ararai/model/ModelConfigParser.kt:163`
  validates only `relativePath.startsWith("models/")`; it does not reject `..`,
  absolute/canonical escape, or non-normalized segments.
- `app/src/main/java/com/jesjobom/ararai/model/ModelFileDownloader.kt:102`
  directly resolves that string with `File(appFilesRoot, config.relativePath)`
  before creating parents and promoting a validated download.
- `app/src/main/java/com/jesjobom/ararai/model/ModelResolver.kt:27` uses the same
  unresolved construction. Existing parser tests cover normal nested paths but
  no traversal/normalization rejection.

Recommended boundary: validate normalized relative segments in the parser and
defend again at every filesystem ownership boundary with canonical containment.
Add parser/resolver/downloader tests for rejected escapes and accepted nested
model paths.

### ARA-AUD-002 evidence

- `app/src/main/java/com/jesjobom/ararai/knowledge/WebSearchPreferences.kt:161`
  suppresses every decryption failure and returns `null`.
- The same file at `:167` and `:195` treats mere preference-key presence as a
  configured credential, so corrupted/invalidated ciphertext can remain enabled.
- `app/src/androidTest/java/com/jesjobom/ararai/WebSearchCredentialStoreTest.kt:18`
  validates the successful save/restore/removal path but not invalidated keys or
  malformed ciphertext.

Recommended boundary: model unreadable credentials explicitly, disable/remove
the unusable provider through a deliberate recovery policy, and add Android
instrumentation coverage without ever logging token contents.

### ARA-AUD-003 evidence

- `app/build.gradle.kts:93` sets `release.isMinifyEnabled = false` while already
  declaring the optimize ProGuard defaults and project rules.
- The internal Play release was observed without R8 metadata/mapping during the
  preceding release validation. No current release minification smoke gate
  proves reflection, Compose, Firebase, LiteRT-LM, or JNI compatibility.

Recommended boundary: first add a signed/unsigned release assembly and focused
runtime smoke strategy, review keep rules and dependency consumer rules, then
enable shrinking and preserve/upload mapping artifacts. Do not treat size alone
as proof of correctness.

### ARA-AUD-004 evidence

- `.github/workflows/android-quality-gate.yml:29` names the step `Set up JDK 17`,
  while `:33` configures `java-version: "21"`.
- `docs/quality-gates.md:33`, `README.md:67`, and `openspec/project.md:71` state
  JDK 17 as the supported build baseline. README separately documents Java 21 as
  a Firebase emulator requirement.

Recommended boundary: choose and document one Gradle runtime for the Android
gate, and isolate any Java 21-only Firebase emulator invocation if both runtimes
are genuinely required.

### ARA-AUD-005 evidence

- `app/src/main/java/com/jesjobom/ararai/model/ModelFileDownloader.kt:43` creates
  an `HttpURLConnection`; `:51-59` throws on non-2xx without closing an error
  stream or disconnecting.
- Successful calls return only `connection.inputStream` at `:73`, losing the
  connection handle; input close normally helps reuse/release but the ownership
  contract is implicit and cannot guarantee explicit disconnect on every path.
- Fallback behavior at `:100-174` may repeat this lifecycle for multiple URLs.

Recommended boundary: make the byte response closeable or otherwise retain a
cleanup callback and prove cleanup on success, HTTP failure, I/O failure, and
cancellation with a controllable connection adapter.

### ARA-AUD-006 evidence

- `app/src/main/java/com/jesjobom/ararai/ui/ArarAiApp.kt` is 2,011 lines and has
  31 touches in the inspected recent history, the highest first-party churn.
- The file suppresses `TooManyFunctions` and contains application navigation,
  home, settings, instruction/tools, provider credentials, benchmark, model
  status, and model-card presentation.
- Archived change `melhoria-7-separar-responsabilidades-da-tela-de-chat`
  demonstrated a successful characterization-first extraction for Chat, and
  `clarify-controller-lifecycle-and-boundaries` deliberately left navigation in
  `ArarAiApp` for that slice rather than declaring the remaining concentration a
  permanent architecture decision.

Recommended boundary: characterize destination-level behavior and extract one
cohesive destination at a time while retaining navigation/controller ownership.
Avoid a single broad rewrite.

## Dependency order

1. ARA-AUD-004 is independent and improves environment reproducibility.
2. ARA-AUD-001, ARA-AUD-002, and ARA-AUD-005 are independent focused hardening
   candidates.
3. ARA-AUD-003 requires release-build/runtime evidence before switching R8 on.
4. ARA-AUD-006 should follow the security/correctness fixes and begin with
   characterization tests; it must not obscure higher-priority scanner findings.

## Considered and rejected

- **Web-search cancellation is swallowed:** rejected. The tool contract
  intentionally serializes cancellation as `SEARCH_CANCELLED`, prevents provider
  fallback on that result, and has explicit unit/instrumentation coverage.
- **Web-search HTTP endpoints permit arbitrary SSRF:** rejected. Production
  transport enforces HTTPS, exact host/path allowlists, no query/userinfo/port,
  disables redirects, bounds responses, and applies timeouts.
- **Chat media deletion accepts arbitrary paths:** rejected. The media repository
  canonicalizes candidates and only owns direct children of the app media root;
  the earlier media-ownership OpenSpec work documents this boundary.
- **Firebase API key in `google-services.json` is a committed secret:** rejected.
  It is public Firebase client configuration; authorization is enforced through
  anonymous Auth, App Check, and Firestore Security Rules. Private signing and
  debug-token material was not found in tracked filenames.
- **Broad `Throwable` catches are automatically vulnerabilities:** rejected as a
  blanket finding. Reviewed generation/transcription paths explicitly preserve
  cancellation or use `finally` for cleanup. Narrowing individual catches may be
  worthwhile during local changes, but no concrete user impact was established
  for this audit register.
- **Pending sideload report failure indicates broken retry:** rejected. Permanent
  App Check failure is intentionally non-retryable and was verified during the
  Play-distribution validation.

## Direction

No product-direction plans are proposed in this round. The repository already
has explicit product constraints and this change is scoped to code quality and
security improvement; speculative features would dilute the audit backlog.

## Coverage limitations

This `improve` pass did not execute Semgrep or CodeQL, perform a Gradle/native CVE
reachability audit, analyze fetched upstream C/C++ source, or prove physical
device, Firebase Console, Play signing, GPU/vendor, memory, or thermal behavior.
Those are explicit later stages in the active OpenSpec change.
