---
title: Managed in-app widgets
permalink: /managed-widgets/
---

# Managed in-app widgets

ArarAI managed widgets are local views authored by an eligible downloaded model
and rendered inside the application. They are not Android launcher widgets. A
model participates only in the user-requested creation or edit flow; confirmed
refreshes execute the stored JavaScript program in the bounded
QuickJS runtime and do not load or prompt a model.

## Create and review

1. Open **Widgets** from Home and select **Create widget**.
2. Describe the view to a downloaded model verified for
   `widget_authoring_pipeline_v1`. ArarAI runs isolated capture-only rounds for
   feasibility and tool selection, a typed algorithm, each tool-call function,
   and `render`. The application derives the `plan` orchestrator locally from
   accepted call functions; that stage performs no model load or generation.
   Every model round uses a fresh conversation, one stage schema, and only the
   already validated dependencies needed by that stage. Protocol version,
   artifact identity, function signature, call alias, tool id, and tool version
   are application-owned rather than requested from the model. An edit
   additionally receives the current normalized widget/source.
3. For a model stage, ArarAI executes exactly one generation, validates it, saves the bounded
   result as a local attempt, unloads the model, and waits. Review the captured
   content and controlled validation result as inert selectable text. **Accept
   and continue** promotes a successful candidate to the checkpoint used by the
   next stage; **Retry** authorizes one repair attempt for the same stage.
4. An accepted stage can be reprocessed without immediately replacing it. The
   prior valid graph remains authoritative until the replacement is accepted;
   accepting it marks dependent checkpoints stale. Attempts are limited to two
   repairs per stage revision, including across application restarts.
5. Review the final unconfirmed source, schedule, tool/runtime/presentation
   permissions, retained-data limits, user actions, and any semantic/source diff.
6. Choose **Create and enable** (or save the replacement revision and enable it)
   or **Save disabled**. Leaving or discarding the draft stores no widget,
   revision, or schedule; discarding also removes the transient authoring
   session, attempts, and checkpoints.

An edit is always a complete replacement revision and always needs confirmation.
Additional tools, runtime values, presentation actions, or a more frequent
schedule are called out as authority expansion. A disabled or unavailable tool
blocks activation; ArarAI does not silently enable it.

The authoring screen is a repository-backed timeline. It distinguishes queued,
running, review, retry, accepted, stale, interrupted, cancelled, and completed
states and shows bounded attempt metadata. Captured JSON and JavaScript are
displayed only as plain text; rejected content is never executed. The user may
leave between stages without cancelling or losing accepted work. Unachievable
requests and requests needing clarification stop before code generation.

Only the installed Gemma 4 E4B advertises the staged widget-authoring protocol
in the checked-in catalog. E2B remains available for Chat and its other tasks,
but not for widget creation or editing. The separate, non-persistent tool-call
diagnostic may still characterize either model in debug builds; it cannot save
a widget.

## Resumable stage attempts

Only the current stage attempt runs in the background. Its foreground
`dataSync` service and ongoing notification exist while that attempt is queued,
checking device readiness, loading, generating, validating, persisting, or
unloading. At the committed boundary the foreground service stops and a normal
notification offers Review, Continue, or Retry when applicable. Notification
actions carry the saved session revision and an action ID, so duplicate or
stale delivery cannot spend a second generation.

Before model load, the application waits up to 10 minutes for the checked-in
thermal and memory criteria while the model remains unloaded. Available-memory
admission includes bounded headroom derived from the selected model artifact.
A recovery-window expiry is reported separately from a model-generation
watchdog timeout, and both are controlled retryable attempt results. Chat,
Voice, diagnostics, and authoring share one application engine whose
generations are serialized.

An achievable feasibility title is requested as a concise name of at most 80
characters; a longer non-blank title is normalized locally instead of spending
a repair. Render validation exercises success, empty, and failed tool outcomes
and reports controlled node-type, field, tone, provenance, empty-result, or
failed-outcome categories without exposing JavaScript exceptions.

The algorithm stage sees brief descriptions of the render capabilities and
selects `collection_card` or `custom_source`. The render stage then receives
only the chosen contract. For a simple collection widget, the model submits
data-only fields: alias, payload-relative array path, item-relative title,
optional body and URL, mandatory fallback, and selection (`first` or
`seeded_random`). The application generates the `render` function and validates
it against populated, empty, and failed result fixtures. The collection-card
helper handles item selection, neutral text, proven links, and fallback.
If the chosen result item has an HTTPS `canonicalUrl`, the helper adds its
source-proven link even when the model omits the optional URL field.
Custom layouts retain the JavaScript source path and its lower-level helpers.
The card can show one unmodified item from one result array; it cannot filter
fetched results, compare them, or choose between multiple result sets. The
algorithm must declare post-result operations as steps and use `custom_source`
for them. A query chosen from the local time before one tool call may still use
a collection card. If no tool can provide the requested data, the existing
feasibility stage must return an unavailable/clarification outcome rather than
silently changing the task. The structural route check does not inspect a
closed list of words in the user's prompt.
`custom_source` can only inspect data the selected tool actually returns; it
cannot filter hidden search candidates from `wikipedia_pages@2`.
Previously accepted algorithm checkpoints without a capability choice continue
through that legacy path; confirmed widget sources are unchanged.

The private widget database stores one non-terminal authoring session, bounded
append-only attempts, and accepted checkpoints. If Android kills the process,
startup marks an in-flight attempt interrupted and restores the timeline,
accepted checkpoints, and consumed retry budget. Final confirmation promotes
one widget revision and removes the transient session in one transaction.
The exact initial request is retained locally with the confirmed revision and
shown with its generated source in widget details for diagnosis. Older revisions
confirmed before this change have no recoverable initial request.

## Refresh and scheduling

Supported periodic intervals are 1, 6, 12, and 24 hours. Android WorkManager
chooses an inexact execution window. Each enabled widget owns one unique
replaceable periodic work definition and a durable execution lease, so manual
and background attempts cannot overlap. Disabling cancels future work and also
blocks manual refresh until the widget is enabled again.
Transient tool unavailability, timeout, or rate limiting keeps the last good
card and asks WorkManager to retry with exponential backoff. A disabled or
misconfigured tool is not retried automatically. The one-hour interval is a
minimum periodic interval, not a promise of an execution at each clock hour:
Android can delay background work substantially while the app or device sleeps.

A refresh validates the active immutable revision and consent, runs `plan`,
dispatches only declared `WIDGET` tools through the application registry, then
runs `render`. `wikipedia_pages@1` sends only a bounded query and language code
for direct stable page lookup. The widget-only `wikipedia_pages@2` searches up
to ten page candidates using metadata, selects `first` or `seeded_random`, and
fetches only the selected page's complete lead introduction and canonical URL.
For v2, the typed algorithm freezes the page tool's `selectionMode`; the
generated call must match it. Since the tool returns one page, a card's own
`seeded_random` setting cannot make a `first` tool call random.
It returns `SummaryTooLarge` if that introduction exceeds the bounded result
budget rather than silently cutting it. New widget drafts see v2; saved v1
widgets and Chat retain v1. Cards can present the complete bounded introduction
as one text node; v2 collection cards use `extract` as the default body even
when a saved renderer specifies only a title. Explicit body configuration wins,
and v1 cards are unchanged. Total result and presentation byte limits still
apply. A typed provider failure, including HTTP 429 rate limiting, records a
controlled failure and retains the last successful card rather than replacing
it with a successful fallback. The UI diagnostic exposes only an allowlisted
reason; it does not include provider text.
`wikipedia_on_this_day@1` sends only numeric
month/day and language code and returns bounded actual historical event items,
not a date-index page. These tools use fixed official Wikipedia HTTPS endpoints;
v1 pages and on-this-day are also available to verified Chat models with
distinct selection instructions.
Provider credentials, headers, endpoints, raw
transport, authoring prompts, Chat history, and unrelated widgets are not given
to the program.

Only a validated presentation tree reaches fixed Compose components. JavaScript
cannot construct Compose, HTML, a WebView, Android intents, or executable UI
callbacks. A Wikipedia article link must exactly match a canonical link in the
structured tool result and must also pass the application's HTTPS Wikipedia
allowlist; it opens only after the user taps it.

Render scripts may use `runtime.isSuccess(outcomes, alias)` and
`runtime.getArray(outcomes, alias, fieldPath)` to read an exact array path
relative to a successful tool payload. `runtime.get` reads other value types
at an exact path. `runtime.linkFor(outcomes, alias,
urlFieldPath, label)` creates a link node with matching source provenance. The
helpers do not recursively search fields or choose the first matching type.
Previously confirmed widget revisions may still access the original outcome
envelope directly. Render authoring receives a compact schema-derived catalog
of result paths and item fields instead of the complete output JSON Schema.

## Local data and failure behavior

The app-private widget database stores the managed definition, immutable source
revisions and digests, active consent, at most one cached presentation, bounded
typed observations, and sanitized run records. The exact checked-in limits and
threat controls are in [managed-widgets-security.md](managed-widgets-security.md).
Android backup and device transfer remain disabled, and there is no widget
export, sharing, or remote synchronization.

A failed, cancelled, unavailable-tool, or malformed run keeps the last valid
presentation visible and marks it stale with the latest controlled attempt
state. Run history contains only revision, timestamps, duration, planned tool
identifiers, outcome, and an allowlisted diagnostic code. It excludes source,
provider content, arbitrary exceptions, stack traces, prompts, credentials, and
raw model/tool protocol.

Deleting a widget requires confirmation, cancels its unique work, and removes
its definition, every source revision, cache, observations, and run history.
Late in-flight results are discarded and cannot recreate deleted state.

## Explicit non-goals

The feature does not provide live-result-dependent/adaptive tool planning,
arbitrary endpoints, downloaded plugins/packages,
currency or financial providers, cross-widget reads, launcher widgets,
notifications, remote sync, public sharing/import, exact wall-clock scheduling,
or unattended model repair/inference.

## Validation boundary

Repository, runtime, tool, scheduling, authorship, consent, renderer-policy, and
end-to-end local-fake tests are automated. Building instrumentation APKs does not
prove real E4B proposal quality, Android lifecycle behavior under load,
WorkManager timing, safe external article opening, or background execution on a
physical arm64 device. Those checks and the evidence format remain in
[device-validation.md](device-validation.md).
