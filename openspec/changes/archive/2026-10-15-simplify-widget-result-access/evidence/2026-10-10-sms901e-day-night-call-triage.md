# SM-S901E day/night call triage (2026-10-10)

JJ stopped an in-progress widget authoring test at the tool-call stage. The
initial request asks for a random Wikipedia page on one quoted topic by day,
another quoted topic by night, and an hourly update. A read-only database
snapshot was inspected; no authoring state was modified on the device.

- Feasibility selected `wikipedia_pages` with an hourly schedule.
- The accepted algorithm described a time-dependent topic transform before a
  single page-search call. Transform steps are descriptions, not executable
  functions in the assembled program.
- The accepted call function instead chose a query from `runtime.language`.
  The night topic did not appear in its returned arguments. Reprocessing the
  call three times produced the same accepted source.
- The prior validator executed the call under different contexts but only
  checked plan shape and tool input validity. It did not check that changing
  the local hour while keeping locale and seed fixed changed the query.

The bounded correction probes a single explicit day/night page query at
10:00 and 22:00 with the same locale and seed. If the query is unchanged, the
call is rejected with a controlled repair hint. The plan/assembly checks also
revalidate accepted call checkpoints after an app update. This detects the
observed omitted time branch; it does not prove every possible natural-
language condition or which topic belongs to which branch.

## Validation and installation (2026-10-11 UTC)

- Targeted temporal-query regression tests and Detekt passed. The full
  `scripts/quality-gate.sh` passed: 762 JVM tests, no failures/skips; Android
  lint, debug and release-candidate builds, R8 artifact checks, runtime APK
  verification, and strict OpenSpec validation (11 passed, 0 failed).
- Debug APK: version `202610101912`, SHA-256
  `463e427794093bee39d7cd7cda4a751d3b20fb347264d51f809d4aeca56994d6`.
  `adb install -r` returned `Success` on SM-S901E. Package inspection changed
  from version `202610100142` to `202610101912`; device-local `lastUpdateTime`
  advanced to `2026-10-10 22:51:02` (America/Toronto).
- The saved authoring session was not mutated. Its accepted tool-call source is
  still wrong; reprocess **Chamada de Ferramenta** before continuing. A new
  physical model generation and day/night widget execution have not yet been
  observed, so the end-to-end semantic result remains unverified.

Follow-up on 2026-10-11: JJ rejected keyword-based authoring barriers. The
day/night lexical guard documented above was removed from the next source
revision. This section remains evidence of what APK `202610101912` contained,
not a statement of the current contract.
