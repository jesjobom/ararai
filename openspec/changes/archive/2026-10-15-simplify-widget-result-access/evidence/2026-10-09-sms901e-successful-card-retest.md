# SM-S901E successful collection-card physical retest

- Read-only inspection on 2026-10-09 UTC. Samsung SM-S901E remained on debug
  APK `202610080024`; Android package manager reported update time
  `2026-10-08 00:26:31` America/Toronto. Device clock read October 8, 21:43
  EDT, timezone `America/Toronto`.
- The authoring session had finalized: no active authoring-session rows, one
  saved managed widget with active revision 1, enabled status, and 24-hour
  periodic interval. The stored program invokes `wikipedia_on_this_day`, uses
  the runtime local date and language, and renders via the generated
  `collectionCard` with `seeded_random` selection. No fixed month/day was
  embedded in the program.
- Three runs of revision 1 completed `Success` without diagnostics between
  `2026-10-09 01:42:21` and `01:42:40` UTC. Each planned
  `wikipedia_on_this_day`. The most recent cached presentation, confirmed by
  a read-only device screenshot, contains a non-fallback title, event text,
  and HTTPS Wikipedia link.
- The selected link carries `sourceAlias=fetch_wikipedia_events` and
  `sourceField=events.18.canonicalUrl`. The Portuguese Wikipedia on-this-day
  events API for local October 8 returned 34 events; item 18 is the 1912
  beginning of the First Balkan War. Its text and page URL match the cached
  presentation. The host-side API request corroborates content; the device's
  successful runs and rendered card are the evidence of device execution.
- The link URL and event text were compared without recording the full tool
  response or generated source in this evidence file. The screenshot and local
  database snapshot were temporary inspection artifacts, not committed.
- Boundary: the 24-hour background schedule is configured but has not had a
  future trigger observed yet. A `Success` run plus card does not independently
  prove that later scheduled refreshes will execute.
