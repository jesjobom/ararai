# npm test-tooling audit — 2026-10-17

This is a snapshot of `npm audit --include=dev` after the non-forced lockfile
refresh and `firebase-tools` 15.34.0 update. The baseline was 31 reported
vulnerable packages (12 moderate, 18 high, 1 critical); the updated graph has
12 (4 moderate, 8 high, 0 critical). Audit counts include dependent packages
that inherit an advisory and are not 12 independent exploitable defects.

The root npm dependencies run only the Firestore Rules emulator/tests and
widget-helper tests. They are development/CI tooling, not APK contents. CI does
run this tooling for pull requests, so development-only does not mean harmless.
No affected path has been shown exploitable in this project's test execution;
the upstream advisories must remain visible until fixed or specifically
verified inapplicable.

## Remaining advisory groups

| Upstream package | Audit nodes | Advisory and current exposure | Update path |
| --- | ---: | --- | --- |
| `@grpc/grpc-js` 1.9.16 | 5 high: itself, `@firebase/firestore`, `@firebase/firestore-compat`, `firebase`, `@firebase/rules-unit-testing` | [TLS authorization context](https://github.com/advisories/GHSA-m9gg-hp2v-232j) and [server error disclosure](https://github.com/advisories/GHSA-f596-whhp-79r4). Our test code uses a client against a local Firestore emulator, not a public gRPC server; exposure to these server-side conditions is not established. | Firebase's current SDK/test packages still resolve this vulnerable transitive version. Monitor a compatible upstream graph; do not force the audit-suggested Firebase 9 downgrade. |
| `braces` 3.0.3 | 3 high: itself, `chokidar`, `firebase-tools` | [Stack exhaustion on deeply nested patterns](https://github.com/advisories/GHSA-vfj7-8cjw-p6xm). The CLI runs a local emulator; the project does not feed untrusted glob patterns intentionally. | `firebase-tools` 15.34.0 is the latest version checked. Wait for a compatible CLI dependency update or verify a targeted override with emulator tests. |
| `@opentelemetry/core` 1.30.1 | 2 moderate: itself, `@google-cloud/pubsub` | [Unbounded W3C Baggage parsing](https://github.com/advisories/GHSA-8988-4f7v-96qf). The local test workflow does not intentionally accept remote tracing headers; exposure is unconfirmed. | Monitor the CLI's Pub/Sub dependency update; a major transitive override needs compatibility testing. |
| `uuid` 9.0.1 | 2 moderate: itself, `gaxios` | [Buffer bounds check](https://github.com/advisories/GHSA-w5hq-g745-h8pq) in specific UUID APIs when a caller supplies a buffer. The project does not call those APIs directly. | Monitor the CLI's HTTP dependency update; do not force a major transitive override without tests. |

`npm audit fix --force` proposes major downgrades of direct Firebase packages
to older releases. That is not a safe automatic remediation. Even a throwaway
lockfile using Firebase 13.0.0 and Rules Unit Testing 6.0.0 still reported the
same 12 audit nodes on this date, so merely upgrading those direct majors did
not clear the residual advisories. Recheck upstream versions and the audit
database during the next planned tooling update.
