# Physical render-stage failure (sanitized)

- Device: Samsung SM-S901E, America/Toronto.
- Installed app at observation: `202610270921`.
- Active authoring model: Gemma 4 E4B (`widget_authoring_pipeline_v1` catalog capability).
- Active session: `AwaitingRetry` at `render`; feasibility, algorithm, call, and plan checkpoints were accepted.
- Accepted algorithm: `collection_list` using `web_search_top_results@1`; the call requested ten results.
- Six render attempts failed with `InvalidPresentationSuccessHandling`, each in 13–23 ms. The deterministic render artifact was captured, so model generation and network access were not the failing operations.
- Root cause: the runtime changed the list from multiple cards to one card, but `hasExpectedCollectionSize` still required a top-level column with three cards in its synthetic success fixture. The test engine also emitted the old shape.
- Resolution: represent each result as a grouped row in one card, count those rows in the validator, and share the successful synthetic presentation fixture between JS runtime and Kotlin authoring tests.
