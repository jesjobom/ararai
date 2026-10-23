# Design

## Integration

Use the platform `AppWidgetProvider` and `RemoteViews` APIs; no extra rendering dependency is required. A configuration activity binds each Android `appWidgetId` to an existing managed widget ID in app-private preferences. The receiver deletes bindings for removed launcher instances. Each render loads the authoritative definition and cached presentation from the existing repository.

The renderer projects the first nonblank text/value leaf as a headline, later leaves as body text, and the first allowlisted Wikipedia link as a separate action after the body. The widget name remains a small label. Headings wrap; body text has no fixed character or line cap and uses the host-provided height. If space is insufficient, Android ellipsizes the visible body, while a tap on the card opens the full in-app presentation. This avoids relying on launcher-specific scrolling behavior. The link reuses the in-app Wikipedia destination allowlist. Rendering never evaluates JavaScript, invokes a tool, or loads a model.

## Update flow

An injected update notifier requests coalesced one-time rendering work after a committed widget run or a confirmed definition mutation. The periodic WorkManager job remains the sole scheduled execution source. Launcher update broadcasts also request rendering work, so the card can recover from process death and launcher recreation without network access or model inference. Rendering failure does not roll back a successful widget execution.

A small refresh control sends an explicit PendingIntent to an app-private receiver. The receiver verifies that the Android instance belongs to this provider and resolves its persisted binding, then enqueues unique one-time execution for the managed widget. The existing execution coordinator commits the new result and notifies the launcher renderer. Disabled, deleted, or unconfigured instances cannot start execution. Multiple taps coalesce while work is pending. The refresh uses the same network-aware worker path as scheduled execution.

## Validation

Unit tests cover presentation projection and mapping/state decisions. Build Android instrumentation APKs without running destructive connected tests on JJ's retained phone. Verify provider registration and installation on the connected SM-S901E; actual Home placement/visual behavior needs a non-destructive device check.
