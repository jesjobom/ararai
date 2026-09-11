---
title: Managed in-app widgets
permalink: /managed-widgets/
---

# Managed in-app widgets

ArarAI managed widgets are local views authored by an eligible downloaded model
and rendered inside the application. They are not Android launcher widgets. A
model participates only while the user is in the foreground creation or edit
flow; confirmed refreshes execute the stored JavaScript program in the bounded
QuickJS runtime and do not load or prompt a model.

## Create and review

1. Open **Widgets** from Home and select **Create widget**.
2. Describe the view to a downloaded model verified for
   `widget_authoring_pipeline_v1`. ArarAI runs isolated capture-only rounds for
   feasibility and tool selection, a typed algorithm, each tool-call function,
   the `plan` orchestrator, and `render`. Every round uses a fresh conversation,
   one stage schema, and only the already validated dependencies needed by that
   stage. An edit additionally receives the current normalized widget/source.
3. ArarAI validates each artifact before the next round. Generated call
   functions run against normal and boundary fixtures; `plan` and `render` run
   with synthetic tool outcomes. At most two repair rounds may replace only the
   rejected stage without changing its frozen authority envelope.
4. Review the final unconfirmed source, schedule, tool/runtime/presentation
   permissions, retained-data limits, user actions, and any semantic/source diff.
5. Choose **Create and enable** (or save the replacement revision and enable it)
   or **Save disabled**. Leaving or discarding the draft stores no widget,
   revision, schedule, prompt, or model exchange.

An edit is always a complete replacement revision and always needs confirmation.
Additional tools, runtime values, presentation actions, or a more frequent
schedule are called out as authority expansion. A disabled or unavailable tool
blocks activation; ArarAI does not silently enable it.

The authoring screen reports the current stage and repair count and supports
cancellation throughout. Intermediate algorithms and fragments remain only in
memory and are never shown as saved or executable. Unachievable requests and
requests needing clarification stop before code generation.

The historical one-shot suite v2 showed that E2B failed the complete proposal
while E4B passed it. That result does not grant eligibility for the new staged
protocol. No checked-in model advertises `widget_authoring_pipeline_v1` until it
passes the physical suite-v3 stage matrix and complete synthetic pipeline.

## Refresh and scheduling

Supported periodic intervals are 1, 6, 12, and 24 hours. Android WorkManager
chooses an inexact execution window. Each enabled widget owns one unique
replaceable periodic work definition and a durable execution lease, so manual
and background attempts cannot overlap. Disabling cancels future work and also
blocks manual refresh until the widget is enabled again.

A refresh validates the active immutable revision and consent, runs `plan`,
dispatches only declared `WIDGET` tools through the application registry, then
runs `render`. `wikipedia_pages@1` sends only a bounded query and language code
for direct stable page lookup. `wikipedia_on_this_day@1` sends only numeric
month/day and language code and returns bounded actual historical event items,
not a date-index page. Both use fixed official Wikipedia HTTPS endpoints and are
also available to verified models with distinct selection instructions.
Provider credentials, headers, endpoints, raw
transport, authoring prompts, Chat history, and unrelated widgets are not given
to the program.

Only a validated presentation tree reaches fixed Compose components. JavaScript
cannot construct Compose, HTML, a WebView, Android intents, or executable UI
callbacks. A Wikipedia article link must exactly match a canonical link in the
structured tool result and must also pass the application's HTTPS Wikipedia
allowlist; it opens only after the user taps it.

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
