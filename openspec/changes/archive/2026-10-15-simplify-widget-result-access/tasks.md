# Tasks

- [x] Add deterministic outcome helpers with exact payload-relative paths and
  preserved link provenance; test success, tool failure, missing/wrong paths,
  multiple aliases, and legacy behavior.
- [x] Replace model-facing render output schema with a concise field catalog
  and helper guidance; test generic nested paths and the Wikipedia event shape.
- [x] Add regression coverage rejecting a success fixture that renders the
  empty/failure fallback, without imposing a tool-specific string on generic
  widgets.
- [x] Run focused tests, the repository quality gate, and strict OpenSpec
  validation. Record any device test not executed.
- [x] Add generic seeded array-item and compact card helpers, make the render
  contract demonstrate them, and test the captured `Math.random()` failure.
- [x] Re-run relevant quality gates and install a debug build for physical
  retest; record the result without claiming generation passed before it does.
- [x] Add a generic collection-card helper and make it the primary model-facing
  render contract for array results. Cover success, empty, failed, nested paths,
  URL provenance, and malformed fields.
- [x] Run focused and relevant broad gates, install a debug build, and record
  that physical authoring remains unverified until a new session is run.
- [x] Disclose brief render-capability descriptions during algorithm authoring;
  provide only the selected capability's full contract at render time. Add a
  typed collection-card artifact with `first` and `seeded_random` selection,
  application-owned source generation, legacy custom-source compatibility, and
  bounded nested result paths.
- [x] Run the full quality gate, install the updated debug APK, and record the
  physical retest boundary.
- [x] Diagnose and fix the local-engine authorization gap for the new
  collection-card capture tool; test the permission path, install the corrected
  APK, and record the next physical retest boundary.
- [x] Diagnose literal text supplied where item-field names are required;
  constrain the selected card tool schema to actual result-field names, improve
  repair guidance, test generic nested paths, and install the corrected APK.
- [x] Give the feasibility catalog balanced, bounded tool-use summaries and
  remove unselected on-this-day implementation guidance from the production
  feasibility context; cover the catalog and tool contracts with tests.
- [x] Distinguish unavailable source data from render logic during existing
  feasibility, describe both render routes in the algorithm stage, and reject
  new `collection_card` algorithms that declare post-result processing or
  multiple tool calls. Preserve runtime-conditioned queries before one call,
  legacy checkpoints, and the exact initial prompts in tests; do not gate on
  a closed vocabulary of user words.
- [x] Physically verify the formerly failing behaviors with representative
  prompts: topic-based page search, local-time-conditioned query selection,
  and `custom_source` for post-result comparison. Check saved programs and
  executions against the retained requests; do not infer semantic fidelity
  from a `Success` run alone. The original Brazil and America/Europe wording
  was not replayed verbatim, and no test guarantees every future model output.
- [x] Retain the initial request on each confirmed revision, expose it in local
  authoring/detail UI, migrate existing databases without losing revisions,
  run the quality gate, and verify a non-destructive device install.
- [x] Make collection cards include the selected item's canonical Wikipedia
  link when the model omits the optional URL field; validate provenance and
  install the fix. Physical refresh of an existing widget remains pending
  because the device was locked during verification.
- [x] Add widget-only `wikipedia_pages@2`: ten metadata candidates, first or
  seeded selection, one complete lead introduction with canonical URL, explicit
  oversize failure, and legacy v1 dispatch/edit compatibility.
- [x] Run the full quality gate and install v2 for physical authoring retest.
- [x] Show complete v2 introductions in existing title-only cards, preserve
  the last good card on typed provider failure, distinguish HTTP 429, run the
  quality gate, and install the fix for physical retest.
- [x] Expose local time and explain that descriptive transform steps do not
  execute. The day/night keyword-based rejection was installed for the first
  physical retest but subsequently removed at JJ's request; semantics must
  remain reviewable rather than a hardcoded lexical gate.
- [x] Freeze page selection in the typed algorithm, require the generated call
  to match it without prompt keyword matching, avoid hourly seed/modulo
  resonance, validate, and install for retest.
- [x] Add nonblocking, localized warnings on reviewable stages for possible
  random/first/last selection and day/night mismatches. Keep accepted status
  and actions unchanged; cover the projection and stage card in tests.
- [x] Scope algorithm `selectionMode` to tools that support tool-level
  selection, distinguish it from post-result selection, and normalize an
  omitted empty dependency list only on the first independent step. Cover the
  physical failure with contract tests, quality gates, and device install.
- [x] Retest the random On This Day event prompt with a year in its title on
  the updated device build; verify that algorithm authoring passes and the
  final card includes the requested year, event description, and source link.

## Validation

- The outstanding semantic cases were covered by later physical authoring
  tests, though not by a literal replay of the original two prompts. A
  Brasil-themed request generated a `wikipedia_pages` query for Brazil rather
  than an unfiltered date feed. A later day/night request generated a
  `wikipedia_pages@2` query branching on the device's local hour between Dog
  and Cat, with seeded selection; the saved widget executed successfully and
  JJ accepted textual search matches. A separate oldest-event request chose
  `custom_source` and compared returned years after the tool call, selecting
  the minimum-year event with a source-bound link. Its card omitted the year,
  an independently documented render defect that the later year/title/body
  retest addressed. These cases verify the authoring routes and data flow,
  not guaranteed semantic compliance for arbitrary future prompts. See
  `evidence/2026-10-12-sms901e-seeded-page-retest.md`,
  `evidence/2026-10-09-sms901e-missing-page-link.md`, and
  `../add-resumable-widget-authoring-workflow/evidence/2026-10-13-sms901e-render-provenance.log`.

- On 2026-10-15, a new SM-S901E authoring run for the retained prompt asking
  for a random Wikipedia event for today with its year in the title completed
  on debug build `202610142346`. The saved program uses local month/day,
  `wikipedia_on_this_day`, and `seeded_random` result selection. Two widget
  runs recorded `Success` without diagnostics; the cached card displays the
  selected article title with the event year, event description, and a link
  bound to the selected item's canonical URL. This closes the specific
  year/title/description physical retest, not the separate Brazil and
  America/Europe semantic retests. See
  `evidence/2026-10-15-sms901e-year-title-description-retest.md`.

- On 2026-10-12, a fresh SM-S901E authoring run completed and saved a widget
  with `wikipedia_pages@2`, a one-hour interval, local-hour query branching,
  and frozen `seeded_random` selection using `runtime.seed`. Three manual runs
  completed successfully; the latest cached card contained an introduction and
  a provenance-bound Wikipedia link. The accepted textual search result does
  not imply semantic topic classification. The automatic hourly trigger has
  not yet elapsed and is not claimed as observed. See
  `evidence/2026-10-12-sms901e-seeded-page-retest.md`.

- The 2026-10-10 summary/fallback fix passed the full quality gate: 759 JVM
  tests, 15 Firestore emulator tests, 11 runtime-helper tests, Android lint,
  debug/instrumentation/release-candidate builds, R8 artifact checks, and
  strict OpenSpec validation (11 items). Debug APK `202610100142` was installed
  on SM-S901E with `adb install -r`; the package version advanced, and the
  observed widget and revision counts remained unchanged. A fresh physical
  card refresh was not performed because the device locked after installation.
  JJ subsequently reported that the refreshed card worked. The saved widget
  remained enabled with a one-hour interval; its WorkManager work was enqueued
  with a 3,600,000 ms interval. This confirms the configured cadence, not an
  exact wall-clock execution time or a completed unattended hourly cycle.
  See `evidence/2026-10-10-sms901e-summary-fallback-triage.md`.

- The 2026-10-09 `wikipedia_pages@2` change passed the full quality gate:
  757 JVM tests, 15 Firestore emulator tests, 10 runtime-helper tests, Android
  lint, debug/instrumentation/release-candidate builds, R8 artifact checks,
  and strict OpenSpec validation (11 items). APK `202610091714` was installed
  on SM-S901E with `adb install -r`; package version and update time advanced.
  Physical authoring and card display remain unverified because the device was
  locked. See `evidence/2026-10-09-sms901e-page-summary-v2-install.md`.

- The 2026-10-09 missing-page-link fix passed 10/10 Node runtime-helper tests,
  targeted JVM tests, and the full quality gate. Debug APK `202610091619`
  was installed on SM-S901E; on-device refresh remains pending because the
  screen was locked. See `evidence/2026-10-09-sms901e-missing-page-link.md`
  (SHA-256 `a8ae89bb94ab866cd318855f33c46f82364a6af5bdda8f3112f53c735ab7006e`).

- The 2026-10-09 prompt-retention change passed targeted SQLite workflow tests
  and the full quality gate. Debug APK `202610090046` was installed with
  `adb install -r` on SM-S901E; the app launched and the retained database
  migrated to schema 3 with all three widget definitions present. Existing
  revisions could not recover already-deleted prompts. See
  `evidence/2026-10-09-sms901e-prompt-retention-install.md` (SHA-256
  `f6ba47601cd83932b3af369991e8e73313c548a8ec516ac9ac9063b8089811c1`).

- `scripts/quality-gate.sh` passed on 2026-10-06, including 737 JVM tests,
  15 Firestore emulator tests, QuickJS host tests, Android lint, debug and
  instrumentation builds, release-candidate R8 build and artifact checks,
  and strict validation of all 11 OpenSpec items.
- `npm run test:widget-runtime-helpers` passed 3/3 Node VM tests against the
  checked-in JavaScript standard-library source.
- The original gate did not include physical-device authoring or refresh. The
  existing confirmed widget revision on JJ's SM-S901E was not edited.
- The 2026-10-07 physical debug retest reached render after successful
  feasibility, algorithm, call, and local plan stages. All three render attempts
  returned `InvalidSource`: their captured sources used prohibited
  `Math.random()`. This is a deterministic-runtime contract error, not a
  Wikipedia service failure. The subsequent helper change requires another
  physical retest.
- After the follow-up helper change, `scripts/quality-gate.sh` passed on
  2026-10-07: 738 JVM tests, 15 Firestore emulator tests, 5 Node helper tests,
  QuickJS host tests, lint, debug/instrumentation and release-candidate APKs,
  R8 artifact checks, and 11/11 OpenSpec items. Debug APK version
  `202610062309`, SHA-256
  `974478f77b9366629cbd09c3eba4a51162f66b5880f05aed33587493f84121ea`
  was installed on SM-S901E with `adb install -r`, preserving app data; Android
  reports `lastUpdateTime=2026-10-06 23:16:23` Toronto. The failed authoring
  session remains present. New physical authoring has **not** yet been run.
- JJ reran physical authoring with that build. The render attempts yielded
  `TimedOut`, `InvalidPresentationFailureHandling`, and
  `InvalidRenderExecution`; see `evidence/2026-10-07-sms901e-render-three-failures.md`.
- After the collection-card change, `scripts/quality-gate.sh` passed on
  2026-10-07: 739 JVM tests, 15 Firestore emulator tests, 7 Node helper tests,
  QuickJS host tests, Android lint, debug and instrumentation builds,
  release-candidate R8/artifact checks, and 11/11 OpenSpec items. Debug APK
  version `202610062350`, SHA-256
  `5769264149eb09f4b7c48f293d21943a90fe9855327aee2d4959ac043ef82d27`,
  was installed with `adb install -r`; Android reports
  `lastUpdateTime=2026-10-06 23:54:45` Toronto. No physical authoring attempt
  with this new build has occurred yet.
- JJ's 2026-10-08 physical retest with that build passed the first four stages
  but all three render attempts failed `InvalidRenderExecution`. Each generated
  source called nonexistent `outcomes.get(...)`; see
  `evidence/2026-10-08-sms901e-collection-card-render-failure.md`. This remains
  an open physical validation failure; the collection-card helper did not
  resolve model-authored JavaScript composition.
- The isolated render context omitted the outcome-map shape even though the
  general authoring API described it. The context now names the plain object,
  exact alias-keyed success/failure envelopes, lack of `get()`, and
  `collectionCard`'s complete-map argument. The targeted test and full quality
  gate passed on 2026-10-08. Debug APK `202610072245` was installed and its
  advanced package update time verified on SM-S901E; see
  `evidence/2026-10-08-sms901e-outcomes-context-install.md` (SHA-256
  `ead93dcacdbc9ed3c9c97cc1e07b3b81ee28b6d13123827f541fba4db96fe9f7`).
  A fresh physical authoring run remains pending.
- JJ reran physical authoring with `202610072245`. The first four stages
  passed, but render attempts failed `InvalidRenderExecution`, then
  `InvalidPresentationFailureHandling` twice. The failures are a missing
  mandatory `collectionCard.fallback` in one branch and nonexistent
  `runtime.text` in two manual failure branches; see
  `evidence/2026-10-08-sms901e-outcomes-context-retest.md` (SHA-256
  `1b7f68d19a896a540892269bf498864baee79cc7ba5b8fd08cc7c6f0c83c69de`).
  A structural simplification of the model-authored render artifact remains
  open.
- The progressive-disclosure and typed collection-card implementation passed
  `scripts/quality-gate.sh` on 2026-10-08, including 9/9 Node runtime-helper
  tests, JVM tests, Firestore tests, QuickJS host tests, Spotless, Detekt,
  Android lint, debug/instrumentation and release-candidate builds, R8 artifact
  checks, and 11/11 strict OpenSpec items. Debug APK `202610072344` was
  installed on SM-S901E; see
  `evidence/2026-10-08-sms901e-progressive-render-install.md` (SHA-256
  `36daf3524a0ba71bd64ec075b408bf47f77a470a21c2df34dea0297481906e1e`).
  Physical authoring with this APK remains unverified. An already accepted
  algorithm checkpoint retains the legacy custom-source path; exercising the
  typed path requires regenerating the algorithm stage or starting a new
  authoring session.
- The first physical session with `collection_card` selected passed the first
  four stages but all three render attempts failed `MissingArtifact`. Device
  logs show `generation_request_rejected` with `tools_unsupported` at each
  attempt: the new ephemeral capture tool was omitted from the local engine's
  allowed authoring-tool set. The set now includes it, with an engine-level
  authorization regression test. Focused and full JVM tests, Spotless, Detekt,
  Android lint and debug build passed; debug APK `202610080007` was installed
  on SM-S901E. See
  `evidence/2026-10-08-sms901e-collection-card-tool-authorization.md` (SHA-256
  `d62b3fbc487fa8b3d12554b6fbfdd9d4d9d7367e875cfebff4ef3039eea52235`).
  The current session can retry render with its accepted `collection_card`
  algorithm. No physical generation with the corrected APK has been run yet.
- JJ's next physical session with `202610080007` reached render and called the
  new capture tool. All three attempts failed `InvalidPresentationFields`:
  the model used literal display text in `title` and `body` instead of item
  property names. The selected card schema now enumerates exact aliases,
  array paths, and string item fields from the chosen tool output schema;
  context and repair instructions distinguish property names from fallback
  display text. JVM tests, Spotless, Detekt, lint, and debug build passed, and
  APK `202610080024` was installed on SM-S901E. See
  `evidence/2026-10-08-sms901e-card-field-enum-install.md` (SHA-256
  `3a49afec0b4cff3944b094b526ceaa3eee2f8895791a57a17308e5215155571f`).
  Physical render with this corrected contract has not yet been rerun.
- JJ's physical run with `202610080024` completed authoring and saved an active
  widget revision. Three widget executions succeeded with no diagnostics, and
  the most recent cached card and on-screen card show a real October 8 event
  with a provenance-bound Wikipedia link. The event text and link match the
  provider's Portuguese October 8 feed. See
  `evidence/2026-10-09-sms901e-successful-card-retest.md`. A future 24-hour
  background trigger has not yet elapsed and is not claimed as tested.
- Two additional physical widgets saved and executed without runtime errors,
  but their compiled programs do not implement the constraints implied by
  their generated display names: `Brasil` has no theme filter, and
  `America at night or Europe during the day` has no hour/region branch. See
  `evidence/2026-10-09-sms901e-two-semantic-mismatches.md`. The finalized
  authoring sessions no longer retain the exact initial prompts, so a literal
  prompt-to-algorithm comparison requires JJ's original prompt text.
- JJ clarified that both prompts requested topic-based normal Wikipedia
  search, not on-this-day events; the second also conditioned the topic on
  local day/night. The existing widget tools expose `wikipedia_pages` query
  search (up to three pages, no verified event classification) and
  `wikipedia_on_this_day` date events (no topic/region query). The production
  compact feasibility context previously carried detailed on-this-day
  guidance whenever that tool was available, biasing selection before a tool
  was chosen. That guidance is no longer exposed at feasibility; both tools
  now have concise capability-boundary summaries. Focused tests, Spotless,
  and Detekt passed. A complete semantic implementation awaits the product
  choice between relevant pages and verified events; no new APK was installed.
