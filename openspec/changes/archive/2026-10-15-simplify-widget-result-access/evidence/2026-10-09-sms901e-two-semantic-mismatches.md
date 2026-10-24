# SM-S901E: two successful widgets with semantic mismatches

- Read-only inspection on 2026-10-09 UTC of Samsung SM-S901E, debug APK
  `202610080024`. The two additional authoring sessions had finalized, so
  their exact initial instructions and intermediate algorithm artifacts were
  no longer present in the app-private database. The generated display names,
  saved program revisions, run records, and cached presentations remain.
- The widget named `Display a random Wikipedia event for the theme "Brasil"`
  has one saved revision and four `Success` runs. Its program calls
  `wikipedia_on_this_day` for the device's local month/day and language, then
  applies `collectionCard` with `seeded_random` to the entire `events` array.
  It contains no search query or predicate for Brasil. The latest displayed
  event is about the Chile–Peru War of the Pacific, not a Brasil-themed event.
- The widget named `Random Wikipedia event for America at night or Europe
  during the day` has one saved revision and six `Success` runs. Its program
  also calls `wikipedia_on_this_day` for local month/day and language and
  selects from the full array with `seeded_random`. It does not read the local
  hour or encode a branch for America versus Europe. At creation shortly after
  midnight in `America/Toronto`, the latest cached event was Kepler's
  supernova; regardless of the selected event, no time-dependent branch exists.
- The `wikipedia_on_this_day` tool result contains year, text, title, and
  canonical URL for each event, but no region or theme classification.
  `collectionCard` supports only `first` or `seeded_random` from an unfiltered
  array. Technical validation and `Success` runs prove execution, not fidelity
  to a theme or conditional selection requirement.
- This evidence does not quote the original user instructions or complete
  generated programs. JJ subsequently clarified that the first prompt asked
  for a random Wikipedia event on the theme Brasil, using normal search rather
  than the date feed; the second asked for theme América at night or Europa
  during the day. The exact wording was approximate, but the mismatch is
  conclusive: neither saved program performs a topic search, and the second
  has no local-hour condition.
