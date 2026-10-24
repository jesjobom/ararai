# SM-S901E render `InvalidSource` after result-access helpers

- Device: Samsung SM-S901E; timezone: America/Toronto (EDT).
- Installed debug APK: version `202610062228`, SHA-256
  `82b6bb9d4f7e010c60d13f845c7ad9df6f2ce2f0ac25dfad3f902ee57d7b362d`.
- Evidence source: local widget-authoring attempt metadata and generated-source
  inspection through `adb run-as`; no user prompt, source, or tool payload is
  retained in this evidence file.
- Feasibility, algorithm, call function, and local plan succeeded once each.
- Render attempts 1, 2, and 3 failed with `InvalidSource`. Their captured
  sources contained `Math.random()`, forbidden by the deterministic source
  policy. The named `render(runtime, outcomes, state)` signature was correct.
- The generated array path was payload-relative `events`, matching the new
  helper API. This failure was not caused by the Wikipedia service or by an
  incorrect result path.
- After the first rejection, repair guidance emphasized the function signature
  but did not explicitly direct replacement of `Math.random()` with a seeded
  selector. The model repeated the prohibited call.

Follow-up: add an application-owned seeded array-item selector and compact card
constructor, update the render/repair contract, verify locally, then retest
physical generation. The failed session is retained on the device until JJ
chooses to discard it; installation alone cannot refill its repair budget.
