## 1. Establish release evidence

- [x] 1.1 Add a reproducible release assembly check on the canonical Java runtime without changing minification yet.
- [x] 1.2 Inventory reflection, serialization, JNI, Firebase, Compose, LiteRT-LM, and dependency consumer-rule boundaries.
- [x] 1.3 Record baseline artifact size and critical release-like smoke scenarios.

## 2. Enable shrinking safely

- [x] 2.1 Add only evidence-backed keep rules and enable optimized R8 for release builds.
- [x] 2.2 Preserve mapping, seeds, usage, and configuration artifacts needed to diagnose release failures.
- [x] 2.3 Keep signing secrets and generated release artifacts outside version control.

## 3. Validate

- [x] 3.1 Run release assembly, unit tests, lint, and the complete quality gate.
- [x] 3.2 Exercise startup, navigation, model load/generation, audio JNI, and database/media flows on a physical device using a release-like artifact; record quota/rate-limit failures and official App Check distribution as accepted exclusions.
- [x] 3.3 Compare size and record mapping availability without treating either as runtime proof.
- [x] 3.4 Run strict OpenSpec validation.
