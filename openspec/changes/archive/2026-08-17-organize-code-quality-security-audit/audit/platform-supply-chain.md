# Android, Firebase, and supply-chain review

## Scope and method

This is the manual boundary review required after the source survey, Semgrep,
and Java/Kotlin CodeQL passes. It covers shipped first-party configuration and
code at baseline `044f66c`; generated output and fetched third-party source are
evidence inputs, not first-party findings.

## Android platform

- The release manifest exposes only the launcher `MainActivity`. The model
  download service is explicitly non-exported. There are no first-party content
  providers, broadcast receivers, deep-link filters, custom permissions, or
  WebViews.
- Camera is declared optional. Camera, microphone, notifications, network, and
  the Android 14 data-sync foreground-service permissions match reachable app
  features; no broad storage, location, contacts, package-query, accessibility,
  overlay, or exact-alarm permission is requested.
- Backup and device transfer are disabled both at the application flag and by
  complete extraction-rule exclusions. Cleartext traffic is disabled.
- Notification actions use explicit component intents with
  `FLAG_IMMUTABLE`. The foreground service is typed as `dataSync`, starts in the
  foreground immediately, owns only catalog model IDs, and cancels owned work
  on destruction.
- Selected images are copied through a bounded importer into app-private
  storage. Recorded WAV files and chat/session data also remain under private
  app directories. The native Whisper wrapper receives app-managed model/audio
  paths; the reviewed release flow does not expose an arbitrary native file
  parser entry point to another application.
- App logs contain model/runtime timing, selected TTS voice/language, and error
  class information, but no prompt text, report payload, API token, or raw media
  was found in first-party logging calls.
- External web content is handled as untrusted model context, response sizes and
  JSON shapes are bounded, provider URLs are constants, Wikipedia language and
  host are constrained, and provider credentials are not included in error
  messages. The previously registered unreadable-credential state remains
  `ARA-AUD-002`; no separate rendering or SSRF finding was demonstrated.

## Firebase and background delivery

- Debug and release install different App Check providers. Release uses Play
  Integrity; the operational Play test recorded in the preceding archived
  change proved `PLAY_RECOGNIZED` plus `LICENSED`, Firestore enforcement, and
  rejection of a sideload.
- Anonymous authentication establishes ownership. Firestore rules accept only
  creates in `generated_content_reports`, bind document ID and `ownerUid` to the
  authenticated UID, use an allowlisted schema with bounded text/context and
  timestamps, permit owner-only point reads, and reject list/update/delete plus
  every other collection.
- The emulator rule suite previously passed 11/11 abuse and valid-write tests.
  WorkManager requires network, uses a stable per-report unique key, exponential
  backoff, and classifies App Check/permission failure as permanent. The device
  test proved offline queue and retry without duplicate persistence.
- Report content is intentionally sent to Firestore after explicit user action;
  raw image/audio is represented only by presence booleans. Retention is encoded
  as a 90-day `expiresAt`; actual backend TTL configuration is an operational
  control and was not independently inspectable from this repository.

No new Android/Firebase security finding was confirmed. The review retains the
existing model-path, credential-recovery, R8, and resource-ownership findings.

## Dependency and artifact controls

- Gradle wrapper distribution `9.4.1` is HTTPS-pinned by
  `distributionSha256Sum`. Repositories are centralized with project-level
  repositories rejected; JitPack is content-filtered to the single VAD group.
- Gradle dependency locking exists for the app, root buildscript, and
  `whisper-runtime`. Strict dependency verification resolved the debug runtime
  graph successfully against `gradle/verification-metadata.xml`.
- The Firebase tooling dependency tree is lockfile-pinned. `npm audit --json`
  reported zero informational, low, moderate, high, or critical advisories
  across its current 724-package audit inventory.
- AboutLibraries generated a 190-library/10-license debug inventory with no
  library lacking a license reference. The repository documents manual review
  of unknown metadata and separate native/model notices before release.
- `whisper.cpp` is pinned to full commit
  `f049fff95a089aa9969deb009cdd4892b3e74916`; network/server/example targets are
  disabled and its MIT attribution is documented. It is fetched from GitHub at
  build time, so availability and upstream-account compromise remain external
  build risks; the immutable commit plus reviewed dependency-verification
  boundary make this an accepted operational limitation rather than a newly
  demonstrated vulnerability.
- All four downloadable catalog artifacts use HTTPS, declared byte sizes, and
  checked-in SHA-256 hashes verified before atomic promotion. Model and native
  licenses/provenance are documented. `ARA-AUD-001` remains important because a
  future catalog path can escape the intended directory before integrity
  validation despite the current catalog being safe.

The release license-inventory task itself requests release signing credentials
because the build script treats every task name containing `release` as a
release-signing request. The equivalent debug inventory was generated for this
read-only audit; release packaging remains covered by the existing signed-build
procedure. This behavior is related to the release/JDK tooling follow-ups, but
does not expose credentials or weaken signing and is not a separate security
finding.

## Advisory coverage limitations

There is no installed Android/Gradle advisory scanner in the approved audit
toolset. Strict resolution, locks, checksums, license inventory, Semgrep,
CodeQL, and the npm advisory service do not prove absence of a vulnerable
transitive Android/native component. No reachable high/critical advisory was
identified from available evidence, but a future CI adoption decision should
evaluate an OSV/Dependabot-class feed separately and pin its data/tool version.

## Raw evidence

Ignored under `.audit/code-quality-security/2026-08-17-044f66c/`:

| Artifact | SHA-256 |
| --- | --- |
| `supply-chain-gradle-dependencies.log` | `391cbaaf37dc7ec7a747ea0dc9bfe446a9cb5b7eb292aa4a75191fe8a3e81df4` |
| `supply-chain-licenses.log` | `97105f05c770ec765ee97b69791d1235886004eff32dd413e4c3c8d39f6ea0d2` |
| `supply-chain-npm-audit.json` | `01a6031e0bf890bb55940591c6e6854bfbb8bd167c67872b36945affb5dbd2d2` |

Generated AboutLibraries evidence:
`app/build/generated/aboutLibraries/debug/res/raw/aboutlibraries.json`, SHA-256
`0cd91ddc8ccc14f8229bea4a6dc3ea77ce15f961106d8814757213c060361737`.

## Reconciliation outcome

- `ARA-AUD-005` is now confirmed at low severity: success-path stream closure
  generally releases the connection, but non-2xx setup paths throw without
  closing an error stream or explicitly disconnecting the owned
  `HttpURLConnection`. The adapter should own and close connection lifetime in
  every outcome and gain a focused test.
- No Semgrep or CodeQL rejection was overturned.
- No new register ID was required. Platform/supply-chain observations either
  map to `ARA-AUD-001` through `ARA-AUD-005`, are documented external
  limitations, or have sufficient existing controls.
