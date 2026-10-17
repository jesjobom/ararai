# SM-S901E retest after outcomes-context prompt fix

- Source: read-only inspection of app-private authoring session and attempt
  records with `adb run-as`. No user prompt or raw model-generated source is
  copied into this evidence file.
- Device: Samsung SM-S901E, America/Toronto (EDT). Installed debug APK
  `202610072245`, update time `2026-10-07 22:49:22` local.
- New authoring session started `2026-10-08 02:53:45` UTC, after installation.
  Feasibility, algorithm, call-function, and local plan stages passed.
- Render attempt 1: `InvalidRenderExecution`. The generated source called
  `collectionCard` without the mandatory `fallback` property on the success
  branch. The helper rejects that configuration on the first synthetic fixture.
- Render attempts 2 and 3: `InvalidPresentationFailureHandling`. Both generated
  a manual failure branch calling nonexistent `runtime.text`. Their success and
  empty branches used `collectionCard` with a fallback; the failure fixture
  entered the invalid custom branch.
- The model no longer called `outcomes.get(...)`, so the prompt fix addressed
  the observed object-shape confusion, but render generation still failed.
  These failures occur during local synthetic validation before any Wikipedia
  fetch, so the service is not implicated.
- No additional production change or APK installation was made in this
  diagnosis. A declarative render artifact would remove model-authored
  branches and helper call composition from the simple collection-card path.
