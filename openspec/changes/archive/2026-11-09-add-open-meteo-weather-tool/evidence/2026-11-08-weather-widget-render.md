# Weather widget render validation — 2026-11-08

- A weather result contains numeric temperature and precipitation fields; the collection-card/list route offers string item fields and can show only the date. New weather algorithms are therefore routed to `custom_source`.
- Render validation now compares two successful weather outcomes with unchanged dates and different measurements. A date-only render fails; a render showing temperature passes. Failure and empty-result fixtures remain separate.
- Targeted widget tests and Detekt passed. `scripts/quality-gate.sh` passed, including Android unit tests, lint, release-candidate assembly, artifact verification, and strict OpenSpec validation.
- Installed release candidate `202611080220` (versionCode `29902040`) on connected SM-S901E via `adb install -r`; installation returned `Success`, and the original app first-install date was preserved.
- The user's saved widget could not be inspected or rerun because the device was locked and the installed release database is not accessible via `run-as`. This code change applies to new or regenerated widget revisions; the exact cause of the saved revision remains unconfirmed.
