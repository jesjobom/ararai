# SM-S901E page-link diagnosis and install

- The newest widget's initial request asked for a random event about Brazil,
  updated hourly. Its confirmed program called `wikipedia_pages@1` with a
  Brazil query and used `collectionCard` on `pages` with a title field but no
  URL field. The latest cached card displayed a Wikipedia page title without
  a link. Three recent executions reported success. This is a semantic
  mismatch as well as a missing optional link: a page result is not a
  verified historical event.
- Root cause of the missing link: `collectionCard` only created a link when
  the model supplied the optional `url` property, despite the selected page
  carrying a `canonicalUrl`.
- Runtime now derives a proven link from the chosen item's HTTPS
  `canonicalUrl` when `url` is omitted; explicit URL fields retain precedence.
  The selected alias and array index remain part of link provenance.
- Node runtime-helper tests passed (10/10); targeted JVM tests and
  `scripts/quality-gate.sh` passed, including strict OpenSpec validation.
- Debug APK version `202610091619`, SHA-256
  `e13f1e6beeb42645cf1f1f9dff81105ba3a42060e876ca23081d27d372f9fc0a`,
  installed by `adb install -r` on SM-S901E. Android reports
  `lastUpdateTime=2026-10-09 16:26:10` device local time.
- The device was locked. A manual widget refresh and visual check of the
  link were not performed; the cached presentation still predates the fix
  until the widget next executes.
