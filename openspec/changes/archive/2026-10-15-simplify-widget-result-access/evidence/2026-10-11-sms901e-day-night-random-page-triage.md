# SM-S901E day/night random-page retest (2026-10-11 UTC)

JJ completed a new physical authoring run on debug APK `202610101912`. A
read-only widget database snapshot was inspected; no saved widget or authoring
state was changed.

- The retained request asks for a random Wikipedia page about one topic by day
  and another by night, with hourly updates.
- The saved call source chooses the query using
  `runtime.currentLocalDateTime().hour`, with local 06:00-17:59 as day. The
  hourly schedule and the language argument are also present.
- Four observed executions completed `Success` with no diagnostic. The
  observed local-night presentation contained the night topic's page, its
  complete introduction, and the canonical Wikipedia link. A daytime run
  has not been observed.
- The call source nevertheless uses `selection: first` for the page tool. Its
  result contains a single selected page. The card's `seeded_random` selector
  therefore cannot fulfill the request for a random page. Also, the provider
  selected the seeded candidate using raw `seed % candidateCount`, which can
  lock an exactly hourly timestamp seed to one index when the candidate count
  divides 3,600,000.

The correction freezes first versus seeded-random selection as a typed
algorithm-step choice, guides and validates the tool call against that choice
without prompt keyword matching, rechecks saved call checkpoints during
plan/assembly, and uses a deterministic pseudorandom generator for candidate
selection. The existing
saved widget keeps its immutable revision and requires recreation or revision
for the random-page behavior; no migration is applied silently.

JJ rejected prompt-keyword-based validation as an authoring barrier. The
bounded day/night matcher from the prior build was removed in the follow-up
source revision. The app still exposes local time and instructs the model to
implement data-selection dependencies in executable calls. The frozen typed
page-selection contract remains a technical consistency check independent of
prompt wording. No heuristic user-facing warning was added in this change.
