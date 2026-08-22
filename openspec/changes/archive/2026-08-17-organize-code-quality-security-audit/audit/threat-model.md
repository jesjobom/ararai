# Lightweight threat model

## Security objectives

ArarAI should preserve the confidentiality and integrity of local conversations,
media, credentials, preferences, and model artifacts; prevent untrusted external
content from crossing privileged boundaries unsafely; keep user-approved report
delivery narrowly scoped; and remain available and recoverable under malformed,
large, interrupted, or adversarial inputs.

This model guides review hypotheses. It does not assert that a vulnerability
exists.

## Trust boundaries and hypotheses

### Android application and platform entry points

- **Boundary:** exported launcher activity, non-exported foreground service,
  runtime permissions, intents, content URIs, lifecycle recreation, WorkManager,
  notifications, camera, microphone, and TTS.
- **Assets:** app-private files, conversation state, media, pending reports,
  preferences, process resources, and user consent state.
- **Hypotheses:** malformed or spoofed intents/URIs, confused-deputy file access,
  exported-component mistakes, permission/state races, unsafe logging, stale work,
  or lifecycle cancellation may expose data, repeat effects, corrupt state, or
  retain resources.

### Local conversation, media, and database storage

- **Boundary:** UI/ViewModels to SQLite, preferences, image import, audio files,
  transcripts, cleanup, backup/transfer configuration, and filesystem ownership.
- **Assets:** private conversation text, images, recordings, transcripts,
  reasoning, report drafts, and media-reference integrity.
- **Hypotheses:** path traversal, oversized/decompression inputs, incomplete
  atomicity, orphan cleanup races, cross-session mix-ups, backup leakage, or
  sensitive exceptions/logs may cause disclosure or data loss.

### Model catalog, download, and native runtime

- **Boundary:** checked-in catalog and hashes, remote artifact hosts, resumable
  downloads, local validation, model parsers, LiteRT-LM, OpenCL/vendor drivers,
  whisper JNI, and native allocation/cancellation.
- **Assets:** executable process integrity, storage, memory, battery/thermal
  budget, validated models, and inference availability.
- **Hypotheses:** redirect or resume confusion, incomplete hash enforcement,
  replacement/race of validated artifacts, malformed model input, integer/size
  errors, JNI lifetime bugs, cancellation races, or resource exhaustion may lead
  to code/native faults, denial of service, or persistent corrupted state.

### External web knowledge and untrusted generated content

- **Boundary:** Tavily/Exa/Wikipedia HTTP transports, stored API credentials,
  remote responses, prompt/tool protocol, Markdown/LaTeX rendering, URLs, and
  model-generated tool arguments.
- **Assets:** API tokens, local prompt/history, network quota, rendering safety,
  and tool authorization.
- **Hypotheses:** prompt injection, SSRF-like URL control, unsafe redirects,
  credential disclosure, unbounded response parsing, misleading citations,
  raw HTML/link activation, or tool-call confusion may cross from untrusted text
  into network, UI, or local-compute effects.

### Credential and preference protection

- **Boundary:** Android Keystore-backed token storage, preference migrations,
  debug/release separation, logs, backups, and app upgrades.
- **Assets:** provider API tokens, signing/debug material, Firebase identities,
  and security configuration.
- **Hypotheses:** plaintext fallback, weak alias/migration behavior, debug provider
  leakage, backup inclusion, exception logging, or failure recovery may disclose
  or silently discard secrets.

### Firebase reporting boundary

- **Boundary:** anonymous authentication, App Check Play Integrity, projection of
  selected report content, private pending queue, WorkManager, direct Firestore
  transport, and Security Rules.
- **Assets:** user-selected report content, pseudonymous owner identity, Firestore
  quota, report immutability, and local-first chat independence.
- **Hypotheses:** projection drift, owner/id mismatch, schema bypass, replay,
  unbounded retry, unauthorized list/update/delete, debug-provider acceptance,
  or enforcement/configuration drift may disclose data, permit abuse, or block
  the required reporting path. Console-side configuration needs operational
  evidence because source analysis cannot prove it.

### Build and supply chain

- **Boundary:** Gradle wrapper/plugins/modules, npm Firebase test tooling, native
  FetchContent, Android SDK/NDK/CMake, model URLs/hashes, CI caches, release
  signing, and Google Play delivery.
- **Assets:** source and release integrity, developer/CI environment, signing
  material, reproducible dependency graphs, and user trust.
- **Hypotheses:** dependency drift, compromised artifacts, insufficient checksum
  coverage, stale reachable CVEs, unsafe build scripts, cache poisoning, license
  mismatch, secret inclusion, or signing misconfiguration may compromise builds
  or releases.

## Adversaries and failure actors

- A malicious or modified client attempting Firebase or local-data abuse.
- A remote service or artifact host returning malicious, malformed, or excessive
  content.
- Adversarial model output or prompt content attempting to influence tools/UI.
- A local app supplying intents/URIs or observing accidentally exposed data.
- A compromised or vulnerable dependency/build input.
- Non-malicious failures: process death, disk exhaustion, flaky networks,
  cancellation races, device/vendor bugs, and configuration drift.

Rooted-device resistance, DRM, model secrecy, and protection from an attacker
with full device/filesystem control are not primary guarantees. The review still
records avoidable exposure that worsens those environments.

## Priority review paths

1. Untrusted input to file, URI, HTTP, renderer, tool, Firestore, or native sinks.
2. Credentials and private conversation/media crossing storage, log, backup, or
   network boundaries.
3. Download validation and use-of-file time-of-check/time-of-use behavior.
4. Authentication/attestation/owner binding and retry/idempotency behavior.
5. JNI/native ownership, size conversions, cancellation, and cleanup.
6. Lifecycle/concurrency paths capable of data loss, duplicate effects, leaks,
   ANRs, or persistent failed state.
7. Dependency/build inputs capable of influencing shipped artifacts.

## Evidence limits

Static analysis cannot prove Google Play/Firebase console policy, real App Check
verdicts, vendor GPU/native behavior, physical permission flows, memory/thermal
behavior, or actual release signing. Findings crossing those boundaries require
the device or operational evidence described by repository documentation.
