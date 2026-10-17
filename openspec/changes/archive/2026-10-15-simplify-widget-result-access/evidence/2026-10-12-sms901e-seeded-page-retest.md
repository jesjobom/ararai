# SM-S901E seeded page-selection retest — 2026-10-12

## Scope

JJ completed a new physical authoring session after the call-stage objective
length regression was corrected. The installed package reported
`versionCode=29862828` and `lastUpdateTime=2026-10-11 21:51:10` in the device's
Toronto timezone. The application database was inspected read-only; the local
copy was deleted after verification.

## Observations

- The new widget was saved and enabled with `periodic_interval_hours=1`.
- The preserved request asked for a random Wikipedia page about dogs by day
  and cats by night, refreshed hourly.
- The confirmed source branches on `runtime.currentLocalDateTime().hour`:
  06:00–17:59 selects `Dog`; 18:00–05:59 selects `Cat`.
- Its `wikipedia_pages@2` call sets `selection="seeded_random"` and
  `seed=runtime.seed`. Runtime contexts use the current time in milliseconds as
  the seed, avoiding a fixed hour-modulo seed. The collection card displays
  `pages[0].title`, the full `extract`, and the `canonicalUrl` link.
- Three runs between 02:22:32 and 02:22:49 UTC had outcome `Success` and no
  diagnostic. The latest presentation contained a Wikipedia summary and link.
- The selected page was a textual match for the generated query `Cat` on
  Portuguese Wikipedia. JJ explicitly accepted text-search semantics, without
  a semantic classification requirement for that result.

This verifies authoring, activation, manual execution, the frozen selection
contract, and result rendering. It does not establish that the first unattended
hourly WorkManager execution occurred, nor does it validate every unsupported
filtering or conditional-selection case covered by the remaining task.
