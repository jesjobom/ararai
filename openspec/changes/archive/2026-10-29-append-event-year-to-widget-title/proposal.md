# Append Wikipedia event year to widget title

## Why

Two accepted revisions of a Wikipedia date-event widget asked for the event year in the title, yet both used a collection card that copied only `title`. The tool supplied `year`, but the card contract had no title formatting field, so authoring silently omitted the requested change.

## What Changes

- Permit a bounded string or integer item field to be appended to a collection-card title in parentheses.
- For an explicit request to include a Wikipedia date-event year in its title, derive the `year` suffix configuration locally in both authoring paths and require a direct card algorithm.
- Preserve the event description and result-proven HTTPS link; leave existing widget revisions unchanged until the user regenerates them.

## Capabilities

### Modified Capabilities

- `llm-widget-authoring`: Preserve explicit title formatting that can be expressed by a bounded collection-card suffix.
- `widget-script-runtime`: Support a bounded item-field title suffix while preserving link provenance.

## Impact

Collection-card runtime and authoring contract, deterministic event-card derivation, and regression tests. No database migration.
