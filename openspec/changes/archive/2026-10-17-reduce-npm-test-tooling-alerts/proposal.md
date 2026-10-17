# Change: Reduce npm Test-Tooling Audit Alerts

## Why

The local quality gate's `npm ci --include=dev` reports 31 security advisories
among the Node packages used for Firestore emulator and widget-helper tests.
They are not packaged into the Android APK, but they execute in development and
CI. A lockfile-only audit fix and a compatible Firebase CLI update can reduce
the reported count without changing Firebase SDK major versions or test APIs.

## What Changes

- Update `firebase-tools` within major version 15 and refresh the npm lockfile
  using non-forced remediation of transitive dependencies.
- Keep exact top-level dependency pins and preserve the current Firestore test
  behavior.
- Record residual advisories and their development-only exposure for follow-up
  rather than forcing incompatible Firebase downgrades or overriding transitive
  packages blindly.

## Impact

- Affected spec: `build-delivery`
- Affected files: npm manifests/lockfile and dependency-update guidance
- No application or APK runtime behavior changes.
