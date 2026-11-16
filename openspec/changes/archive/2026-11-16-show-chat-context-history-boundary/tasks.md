## Implementation

- [x] Project a contiguous suffix of eligible complete user turns within the existing approximate input budget; do not backfill older short turns or split user/assistant pairs.
- [x] Resolve the boundary through bounded persistence reads beyond the current 512-message ceiling when a larger context requires it, off the main thread.
- [x] Expose inclusion metadata from the authoritative projection and preserve correct LiteRT-LM conversation reuse/invalidation when the suffix changes.
- [x] Add the accessible Chat timeline divider or truthful older-page indicator, recalculating it from active settings and request state without persisting it.
- [x] Add targeted tests for projection, dynamic boundary, pagination, modality eligibility, and retained native state.
- [x] Reserve an additional 10% of context capacity in the shared approximate projection and verify the Chat boundary follows that selection.
- [x] Update relevant documentation and run targeted tests, Android formatting/static analysis, full JVM tests, lint/build, and strict OpenSpec validation.
- [x] Complete a non-destructive visual check of the divider between excluded and included messages. JJ confirmed the manual in-history boundary check on 2026-11-16; see `evidence/2026-11-15-sm-s901e-check.md`.
