# SM-S901E render retest: three distinct failures

- Device: Samsung SM-S901E; timezone America/Toronto (EDT).
- Debug APK: version `202610062309`, SHA-256
  `974478f77b9366629cbd09c3eba4a51162f66b5880f05aed33587493f84121ea`.
- Evidence source: read-only local authoring attempt metadata and generated-source
  inspection with `adb run-as`. No prompt, generated source, or provider payload
  is retained in this evidence file.
- Feasibility, algorithm, call function, and local plan succeeded once each.
- Render attempt 1: `TimedOut`; no capture artifact.
- Render attempt 2: `InvalidPresentationFailureHandling`; generated source read
  the array before checking outcome success, so the failed-outcome fixture
  threw. It also redundantly selected an index before using `pickArrayItem`.
- Render attempt 3: `InvalidRenderExecution`; generated source supplied the URL
  value to `linkFor` where the API requires the result-relative URL *path*.

These are three independent failures. The second and third show that composing
low-level selection, fallback, and provenance helpers still overloads the model.
Follow-up: add one generic `collectionCard` helper that owns all of those
operations for a schema-declared array result, while retaining the low-level
API for more complex layouts and existing saved widgets.
