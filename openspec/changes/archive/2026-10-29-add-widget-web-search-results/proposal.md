# Add search-only web results to managed widgets

## Why

Widgets need recurring, linkable search results, including a first-ranked result or a list of up to ten. Chat's existing `web_search@1` is a focused-evidence contract capped at three sources and may extract page content, so exposing it unchanged would not meet this use case.

## What changes

- Add a widget-only, versioned `web_search` results contract backed by the configured Exa/Tavily provider chain. It performs provider search only, returns ranked title/HTTPS URL metadata, and never requests page extraction or full content.
- Keep Chat's `web_search@1` behavior and provider ordering unchanged. Widget enablement is independent; encrypted, verified provider credentials remain shared.
- Support one or up to ten ranked result cards with source-proven links. Treat first-ranked as the provider's top result, not a verified answer or stock/election fact.
- Keep periodic refresh within the existing 1/6/12/24-hour inexact WorkManager schedule and existing revision-specific consent.

## Impact

Web search provider adapters, widget registry and preferences, tool management UI, result/link validation, authoring guidance, tests, and documentation.
