# Organize Chat and widget tools

## Why

Users can manage tools in Assistant configuration, but cannot clearly discover or manage the tools available to widgets. The current Wikipedia switch also controls both consumers, while widget authoring links to the Chat-oriented tool screen when a tool is blocked.

## What Changes

- Keep the existing managed-widget list as the Widgets landing screen, with creation in its current position and a top-right tools icon opening widget tools.
- Present Chat and widget tool availability in their respective areas, including eligibility, enabled/ready state, and the reason a tool cannot be used.
- Separate per-consumer enablement from provider configuration. A provider credential and its verification are shared where the same integration serves both consumers; enabling one consumer does not enable the other.
- Put a configuration action on a tool that opens a provider-owned dialog for simple credentials, including validation and removal. Allow a dedicated flow for providers whose configuration cannot fit a dialog, without requiring a new global credentials area now.
- Preserve each widget revision's explicitly confirmed tool permissions. A shared credential or widget-wide enablement never grants an existing revision new tools.
- Migrate current preferences without unexpectedly changing effective Chat or widget availability. Do not make web search widget-eligible as part of this change.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `managed-widgets`: Add widget tool management from the existing list and context-correct navigation from authoring.
- `local-llm-hub`: Clarify the Chat Tools surface and its provider configuration action.
- `application-tool-platform`: Define independent consumer enablement and shared provider configuration/readiness.

## Impact

Compose navigation and tool screens, tool operational-state resolution, locally persisted preferences and migration, provider credential UI, widget authoring recovery links, and UI/unit tests. Existing widget definitions and their confirmed revision contracts remain unchanged.
