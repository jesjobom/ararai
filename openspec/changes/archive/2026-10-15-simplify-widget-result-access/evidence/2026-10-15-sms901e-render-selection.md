# SM-S901E: render-stage selection failure, 2026-10-15

The persisted authoring session for a random On This Day event with its year
in the title advanced through algorithm revision 4, call revision 2, and the
deterministic plan. All three render attempts in revision 1 failed
`InvalidSource`.

Every generated source used `Math.random()`, which the bounded JavaScript
source parser prohibits. It also called `outcomes.getArray(...)`, but
`outcomes` is a plain object; the helper is
`runtime.getArray(outcomes, alias, path)`. The first attempt additionally
called `runtime.isSuccess` on an array, not on `(outcomes, alias)`. These
attempts were rejected before widget installation or execution.

The render-stage objective, API context, and repair text now explain
`runtime.pickArrayItem(outcomes, alias, path, 'seeded_random')`, which returns
`{item,path}` or null, and the correct signatures for comparison helpers.
They explicitly prohibit `Math.random()`. The selected item's path can be
passed to `runtime.linkFor` for a provenance-bound link. This is generic
guidance for first/random selection versus comparison, not a fixed
Wikipedia-specific rendering template. Model success remains unverified until
the saved render stage is retried on the device.

Directed tests and the complete Spotless, Detekt, debug unit test, lint and
APK build gate passed; strict OpenSpec validation and `git diff --check`
passed. Debug APK SHA-256
`d221d24050f8c249e4b37bd39a58b92119be077647db05be25bb8b9882a96136`
installed successfully. Android reports version `202610142309` and local
update time `2026-10-14 23:13:13`; the authoring session remains persisted
at `AwaitingRetry` / `render` revision 84.
