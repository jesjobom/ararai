# SM-S901E collection-card retest: render execution fails three times

- Device: Samsung SM-S901E, America/Toronto (EDT).
- Installed debug APK: version `202610062350`, SHA-256
  `5769264149eb09f4b7c48f293d21943a90fe9855327aee2d4959ac043ef82d27`.
- Evidence source: read-only inspection of the app-private authoring attempt
  records via `adb run-as`. User prompt and generated source are not retained
  in this evidence file.
- Feasibility, algorithm, call function, and local plan stages succeeded.
- Render attempts 1, 2, and 3 each captured source and failed with
  `InvalidRenderExecution`.
- Every captured render source called `outcomes.get(...)`. The runtime passes
  `outcomes` as a plain JavaScript object; it has no `get` method. This call
  occurs before the helper invocation and throws on the first validation
  fixture.
- Attempt 1 also used an alias not present in the accepted plan and referenced
  a nonexistent `runtime.random()` helper. Attempts 2 and 3 submitted the same
  source as each other, passing a single outcome value where `collectionCard`
  expects the complete outcomes map. These additional defects are downstream
  of the first exception and were not independently classified by validation.
- The failed `InvalidRenderExecution` repair already omitted rejected source
  to avoid anchoring, yet the model regenerated the same invalid pattern.

Conclusion: adding higher-level JavaScript helpers alone has not removed the
model's obligation to compose JavaScript and pass the correct data structures.
The next change should consider a declarative render artifact: the model
selects a bounded alias and schema-derived field names, while the application
validates that description and generates the JavaScript render function. Do
not silently reinterpret `outcomes.get` or broaden `collectionCard` to guess
aliases; either would conceal wrong bindings and risk provenance errors.
