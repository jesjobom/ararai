# Add bounded web excerpts to widgets

## Why

The existing widget web-search contract returns titles and links only. Some recurring widgets need the same bounded excerpts already produced by Chat's configured web-search providers, without loading a model at refresh time.

## What changes

- Add `web_search_content@1` for widgets, reusing the existing Exa/Tavily focused-evidence chain and returning up to three structured results with bounded excerpts and source URLs.
- Keep `web_search_top_results@1` as a parallel search-only option for new widgets (up to ten titles/links), and keep Chat's `web_search@1` unchanged.
- Give the widget variants distinct display names and expose both in widget tool setup and new-widget authoring. Continue requiring widget-specific enablement, configured credentials, and per-revision consent.

## Impact

Web-evidence normalization, widget tool registry and authoring catalog, tool setup UI, tests, and capability documentation. No page HTML or unrestricted scraping is exposed.
