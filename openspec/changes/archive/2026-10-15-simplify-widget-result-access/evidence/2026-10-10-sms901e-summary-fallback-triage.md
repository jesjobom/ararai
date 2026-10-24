# SM-S901E page-summary card triage (2026-10-10)

JJ reported that a new page-search widget displayed only a title and link, and
that repeated refreshes occasionally displayed only its fallback text. The
installed application was `202610091714`. A read-only local copy of the
application database was inspected; it was not modified on the device.

- The saved revision uses `wikipedia_pages@2`. Its card configuration provides
  `items=pages` and `title=title`, but no body field. The cached presentation
  contains a title and a canonical link, with no introduction text.
- The tool's page result contains a full lead `extract`; the live search for
  the widget's topic returned ten candidates with nonempty leads below the
  tool's configured result bounds. This rules out missing lead data or an
  oversize result for those observed candidates, not all future results.
- Sixteen recorded refreshes were labelled `Success` with no diagnostic,
  including the period when JJ observed the fallback. The old coordinator
  checked dispatch rejections, but not a provider payload of
  `kind=failure`; a renderer fallback could therefore be stored as success.
- The recorded data do not contain the provider's HTTP status for those
  refreshes. HTTP 429, a transient network failure, and other typed provider
  failures cannot be distinguished retrospectively. There is no configured
  fixed request quota in the widget's application-side page-search tool.

The fix supplies `extract` as the v2 result's default card body, while leaving
explicit card configuration and legacy v1 output unchanged. Typed provider
failures now fail the run without overwriting its last successful card. The
HTTP transport maps 429 to an allowlisted rate-limit diagnostic; other provider
content is not included in the diagnostic.

Physical refresh after installation is a separate verification step and is
not inferred from the tests or database snapshot. The full quality gate passed
with 759 JVM tests, 15 Firestore tests, 11 runtime-helper tests, Android lint,
debug/instrumentation/release-candidate builds, R8 artifact checks, and 11
strict OpenSpec items. APK `202610100142` installed successfully with
`adb install -r`; the device reported that version after installation. The
observed widget still had one saved revision and sixteen historical runs, as
before installation. The device then showed its lock screen, so no new refresh
or on-screen presentation was claimed during that installation check. JJ later
reported that the refreshed card worked. A subsequent read-only device check
found the widget enabled with `periodic_interval_hours=1` and a matching
enqueued WorkManager periodic work specification with
`interval_duration=3600000` ms. Android may defer actual execution; an
unattended hourly cycle has not been observed here.
