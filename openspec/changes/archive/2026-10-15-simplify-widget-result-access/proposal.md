# Proposal

## Why

A physically generated Wikipedia widget passed every authoring stage but rendered
its empty-result fallback for a nonempty result. Its render function read
`outcomes[alias].events` instead of `outcomes[alias].payload.events` and built an
incomplete link provenance path. The model-facing contract currently exposes a
transport envelope, a tool-specific payload schema, and presentation provenance
as separate details that the model must combine correctly.

## What Changes

- Add application-owned, deterministic render helpers for checking a named tool
  outcome, reading a typed array by a payload-relative path, and creating a
  result-proven link from a payload-relative path.
- Show render authors a concise result-field catalog and helper examples instead
  of the raw output schema and transport-envelope navigation instructions.
- Require exact paths. Do not recursively search by field name or silently pick
  the first field of a type.
- Keep the existing outcome wire shape and legacy JavaScript access working for
  confirmed widget revisions; the simplified API is additive.
- Test both the new helpers and the physical failure mode in authoring
  validation.
- After the first physical retest, add a seeded array-item selector and a
  compact card builder so the model does not write random-index logic or
  repetitive presentation-node envelopes.
- After a second physical retest showed failed-outcome and link-path composition
  errors, add a single generic collection-card operation that owns selection,
  fallback, presentation wrapping, and link provenance for simple array cards.
- Add a widget-only `wikipedia_pages@2` contract that searches more candidates
  using metadata, selects first or seeded, then retrieves only that page's
  complete lead introduction and canonical URL. Keep v1 executable for saved
  widgets and Chat; expose only the latest page version for new widget drafts.

## Impact

- `widget-script-runtime` and `llm-widget-authoring` contracts, QuickJS standard
  library, render prompt construction, validation, and targeted tests.
- No new provider, storage, or JavaScript authority. V2 adds one bounded
  Wikipedia API request per invocation. No change to persisted revisions or
  user data.
