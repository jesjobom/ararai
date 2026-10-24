# SM-S901E: random On This Day event with year and description

The newly authored widget retained the initial request to show a random
Wikipedia event for the local date and add the event year to its title. The
installed debug package reported version `202610142346`.

The saved revision calls `wikipedia_on_this_day` with the runtime's local
month and day. Its render source uses `runtime.pickArrayItem` with
`seeded_random`, builds a title from the selected item's article title and
year, includes the same item's event description as a separate body field,
and creates a provenance-bound link to that item's `canonicalUrl`.

Two observed executions at 04:18 UTC on 2026-10-15 recorded `Success` with
no diagnostic. The cached card contains all three requested parts: title with
year, event text, and source link. This verifies this physical authoring and
execution case. It does not establish that every future model generation will
preserve these semantics, nor does it verify unattended scheduling.
